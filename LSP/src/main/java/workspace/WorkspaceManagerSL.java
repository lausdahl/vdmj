/*******************************************************************************
 *
 *	Copyright (c) 2020 Nick Battle.
 *
 *	Author: Nick Battle
 *
 *	This file is part of VDMJ.
 *
 *	VDMJ is free software: you can redistribute it and/or modify
 *	it under the terms of the GNU General Public License as published by
 *	the Free Software Foundation, either version 3 of the License, or
 *	(at your option) any later version.
 *
 *	VDMJ is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public License
 *	along with VDMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package workspace;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map.Entry;
import java.util.Vector;

import com.fujitsu.vdmj.Settings;
import com.fujitsu.vdmj.ast.modules.ASTModule;
import com.fujitsu.vdmj.ast.modules.ASTModuleList;
import com.fujitsu.vdmj.in.INNode;
import com.fujitsu.vdmj.in.modules.INModuleList;
import com.fujitsu.vdmj.lex.Dialect;
import com.fujitsu.vdmj.lex.LexTokenReader;
import com.fujitsu.vdmj.mapper.ClassMapper;
import com.fujitsu.vdmj.messages.VDMMessage;
import com.fujitsu.vdmj.runtime.ModuleInterpreter;
import com.fujitsu.vdmj.syntax.ModuleReader;
import com.fujitsu.vdmj.tc.TCNode;
import com.fujitsu.vdmj.tc.definitions.TCDefinition;
import com.fujitsu.vdmj.tc.modules.TCModule;
import com.fujitsu.vdmj.tc.modules.TCModuleList;
import com.fujitsu.vdmj.typechecker.ModuleTypeChecker;
import com.fujitsu.vdmj.typechecker.TypeChecker;

import dap.DAPMessageList;
import dap.DAPRequest;
import json.JSONArray;
import json.JSONObject;
import lsp.Utils;
import lsp.textdocument.SymbolKind;
import rpc.RPCErrors;
import rpc.RPCMessageList;
import rpc.RPCRequest;
import rpc.RPCResponse;
import vdmj.LSPDefinitionFinder;

public class WorkspaceManagerSL extends WorkspaceManager
{
	private ASTModuleList astModuleList = null;
	private TCModuleList tcModuleList = null;
	private INModuleList inModuleList = null;

	public WorkspaceManagerSL()
	{
		Settings.dialect = Dialect.VDM_SL;
	}
	
	@Override
	protected List<VDMMessage> parseURI(URI uri)
	{
		List<VDMMessage> errs = new Vector<VDMMessage>();
		StringBuilder buffer = projectFiles.get(uri);
		
		LexTokenReader ltr = new LexTokenReader(buffer.toString(),
				Dialect.VDM_SL, new File(uri), Charset.defaultCharset().displayName());
		ModuleReader mr = new ModuleReader(ltr);
		mr.readModules();
		
		if (mr.getErrorCount() > 0)
		{
			errs.addAll(mr.getErrors());
		}
		
		if (mr.getWarningCount() > 0)
		{
			errs.addAll(mr.getWarnings());
		}

		return errs;
	}

	@Override
	protected RPCMessageList checkLoadedFiles() throws Exception
	{
		astModuleList = new ASTModuleList();
		List<VDMMessage> errs = new Vector<VDMMessage>();
		List<VDMMessage> warns = new Vector<VDMMessage>();
		
		for (Entry<URI, StringBuilder> entry: projectFiles.entrySet())
		{
			LexTokenReader ltr = new LexTokenReader(entry.getValue().toString(),
					Dialect.VDM_SL, new File(entry.getKey()), Charset.defaultCharset().displayName());
			ModuleReader mr = new ModuleReader(ltr);
			astModuleList.addAll(mr.readModules());
			
			if (mr.getErrorCount() > 0)
			{
				errs.addAll(mr.getErrors());
			}
			
			if (mr.getWarningCount() > 0)
			{
				warns.addAll(mr.getWarnings());
			}
		}
		
		if (errs.isEmpty())
		{
			tcModuleList = ClassMapper.getInstance(TCNode.MAPPINGS).init().convert(astModuleList);
			tcModuleList.combineDefaults();
			TypeChecker tc = new ModuleTypeChecker(tcModuleList);
			tc.typeCheck();
			
			if (TypeChecker.getErrorCount() > 0)
			{
				errs.addAll(TypeChecker.getErrors());
			}
			
			if (TypeChecker.getWarningCount() > 0)
			{
				warns.addAll(TypeChecker.getWarnings());
			}
		}
		else
		{
			tcModuleList = null;
		}
		
		if (errs.isEmpty())
		{
			inModuleList = ClassMapper.getInstance(INNode.MAPPINGS).init().convert(tcModuleList);
		}
		
		errs.addAll(warns);
		return diagnosticResponses(errs, null);
	}

	@Override
	public RPCMessageList findDefinition(RPCRequest request, URI uri, int line, int col)
	{
		if (tcModuleList != null)
		{
			LSPDefinitionFinder finder = new LSPDefinitionFinder();
			TCDefinition def = finder.find(tcModuleList, new File(uri), line + 1, col + 1);
			
			if (def == null)
			{
				return new RPCMessageList(request, RPCErrors.InvalidRequest, "Definition not found");
			}
			else
			{
				URI defuri = Utils.fileToURI(def.location.file);
				
				return new RPCMessageList(request,
						new JSONArray(
							new JSONObject(
								"targetUri", defuri.toString(),
								"targetRange", Utils.lexLocationToRange(def.location),
								"targetSelectionRange", Utils.lexLocationToPoint(def.location))));
			}
		}
		else
		{
			return new RPCMessageList(new RPCResponse(request, "Specification has errors"));
		}
	}

	@Override
	protected FilenameFilter getFilenameFilter()
	{
		return Dialect.VDM_SL.getFilter();
	}

	@Override
	public RPCMessageList documentSymbols(RPCRequest request, URI uri)
	{
		JSONArray results = new JSONArray();
		File file = new File(uri);
		
		for (TCModule module: tcModuleList)
		{
			if (module.files.contains(file))
			{
				results.add(symbolInformation(module.name, SymbolKind.Module, null));
				
				for (TCDefinition def: module.defs)
				{
					for (TCDefinition indef: def.getDefinitions())
					{
						results.add(symbolInformation(indef.name, indef.getType(), SymbolKind.kindOf(indef), indef.location.module));
					}
				}
			}
		}
		
		return new RPCMessageList(request, results);
	}

	@Override
	public ModuleInterpreter getInterpreter() throws Exception
	{
		if (interpreter == null)
		{
			interpreter = new ModuleInterpreter(inModuleList, tcModuleList);
		}
		
		return (ModuleInterpreter) interpreter;
	}

	@Override
	public DAPMessageList threads(DAPRequest request)
	{
		return new DAPMessageList(request, new JSONObject("threads", new JSONArray(1L)));
	}

	@Override
	public DAPMessageList evaluate(DAPRequest request, String expression, String context)
	{
		try
		{
			for (ASTModule m: astModuleList)
			{
				if (m.name.name.equals(expression))
				{
					interpreter.setDefaultName(expression);
					DAPMessageList responses = new DAPMessageList(request);
					responses.add(prompt());
					return responses;
				}
			}
			
			return super.evaluate(request, expression, context);
		}
		catch (Exception e)
		{
			DAPMessageList responses = new DAPMessageList(request, e);
			responses.add(prompt());
			return responses;
		}
	}
}