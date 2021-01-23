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

package vdmj;

import java.io.File;
import java.util.Set;

import com.fujitsu.vdmj.ast.lex.LexNameToken;
import com.fujitsu.vdmj.lex.LexLocation;
import com.fujitsu.vdmj.tc.TCNode;
import com.fujitsu.vdmj.tc.definitions.TCClassDefinition;
import com.fujitsu.vdmj.tc.definitions.TCClassList;
import com.fujitsu.vdmj.tc.definitions.TCDefinition;
import com.fujitsu.vdmj.tc.definitions.TCLocalDefinition;
import com.fujitsu.vdmj.tc.expressions.TCFieldExpression;
import com.fujitsu.vdmj.tc.expressions.TCIsExpression;
import com.fujitsu.vdmj.tc.expressions.TCMkTypeExpression;
import com.fujitsu.vdmj.tc.expressions.TCVariableExpression;
import com.fujitsu.vdmj.tc.lex.TCIdentifierToken;
import com.fujitsu.vdmj.tc.lex.TCNameToken;
import com.fujitsu.vdmj.tc.modules.TCModule;
import com.fujitsu.vdmj.tc.modules.TCModuleList;
import com.fujitsu.vdmj.tc.statements.TCCallObjectStatement;
import com.fujitsu.vdmj.tc.statements.TCCallStatement;
import com.fujitsu.vdmj.tc.statements.TCIdentifierDesignator;
import com.fujitsu.vdmj.tc.types.TCClassType;
import com.fujitsu.vdmj.tc.types.TCField;
import com.fujitsu.vdmj.tc.types.TCParameterType;
import com.fujitsu.vdmj.tc.types.TCRecordType;
import com.fujitsu.vdmj.tc.types.TCUnresolvedType;
import com.fujitsu.vdmj.typechecker.Environment;
import com.fujitsu.vdmj.typechecker.ModuleEnvironment;
import com.fujitsu.vdmj.typechecker.NameScope;
import com.fujitsu.vdmj.typechecker.PrivateClassEnvironment;
import com.fujitsu.vdmj.typechecker.PublicClassEnvironment;

import workspace.Log;

public class LSPDefinitionFinder
{
	public static class Found
	{
		public TCModule module;
		public TCClassDefinition classdef;
		public TCNode node;
		
		public Found(TCModule module, TCClassDefinition clssdef, TCNode node)
		{
			this.module = module;
			this.classdef = clssdef;
			this.node = node;
		}
	}
	
	public Found findLocation(TCModuleList modules, LexLocation position)
	{
		LSPDefinitionLocationFinder finder = new LSPDefinitionLocationFinder();
		
		for (TCModule module: modules)
		{
			// Only explicit modules have a span, called (say) "M`M"
			LexNameToken sname = new LexNameToken(module.name.getName(), module.name.getLex());
			LexLocation span = LexLocation.getSpan(sname);
			
			if (span == null || position.within(span))
			{
				for (TCDefinition def: module.defs)
				{
					Set<TCNode> nodes = def.apply(finder, position);
					
					if (nodes != null && !nodes.isEmpty())	// found it!
					{
						TCNode node = nodes.iterator().next();
						return new Found(module, null, node);
					}
				}
			}
		}

		return null;
	}
	
	public Found findLocation(TCClassList classes, LexLocation position)
	{
		LSPDefinitionLocationFinder finder = new LSPDefinitionLocationFinder();
		
		for (TCClassDefinition cdef: classes)
		{
			LexLocation span = LexLocation.getSpan(cdef.name.getLex());
			
			if (span == null || position.within(span))
			{
				for (TCDefinition def: cdef.definitions)
				{
					Set<TCNode> nodes = def.apply(finder, position);
					
					if (nodes != null && !nodes.isEmpty())	// found it!
					{
						TCNode node = nodes.iterator().next();
						return new Found(null, cdef, node);
					}
				}
			}
		}

		return null;
	}
	
	public TCDefinition findDefinition(TCModuleList modules, File file, int line, int col)
	{
		return findDefinition(modules, new LexLocation(file, "?", line, col, line, col));
	}
	
	public TCDefinition findDefinition(TCModuleList modules, LexLocation position)
	{
		Found found = findLocation(modules, position);
		
		if (found != null)
		{
			TCModule module = found.module;
			ModuleEnvironment env = new ModuleEnvironment(module);
			TCDefinition result = findDefinition(found.node, env, module.name.getName());

			if (result == null)
			{
				Log.error("TCNode located, but unable to find definition of %s %s",
						found.node.getClass().getSimpleName(), position);
			}
						
			return result;
		}
		else
		{
			Log.error("Unable to locate symbol %s", position);
			return null;
		}
	}
	
	public TCDefinition findDefinition(TCClassList classes, File file, int line, int col)
	{
		return findDefinition(classes, new LexLocation(file, "?", line, col, line, col));
	}
	
	public TCDefinition findDefinition(TCClassList classes, LexLocation position)
	{
		Found found = findLocation(classes, position);
		
		if (found != null)
		{
			TCClassDefinition cdef = found.classdef;
			PublicClassEnvironment globals = new PublicClassEnvironment(classes); 
			PrivateClassEnvironment env = new PrivateClassEnvironment(cdef, globals);
			TCDefinition result = findDefinition(found.node, env, cdef.name.getName());

			if (result == null)
			{
				Log.error("TCNode located, but unable to find definition of %s %s",
						found.node.getClass().getSimpleName(), position);
			}
			
			return result;
		}
		else
		{
			Log.error("Unable to locate symbol %s", position);
			return null;
		}
	}
	
	private TCDefinition findDefinition(TCNode node, Environment env, String fromModule)
	{
		if (node instanceof TCVariableExpression)
		{
			TCVariableExpression vexp = (TCVariableExpression)node;
			return vexp.getDefinition();
		}
		else if (node instanceof TCCallStatement)
		{
			TCCallStatement stmt = (TCCallStatement)node;
			return stmt.getDefinition();
		}
		else if (node instanceof TCCallObjectStatement)
		{
			TCCallObjectStatement stmt = (TCCallObjectStatement)node;
			return stmt.getDefinition();
		}
		else if (node instanceof TCIdentifierDesignator)
		{
			TCIdentifierDesignator id = (TCIdentifierDesignator)node;
			return env.findName(id.name, NameScope.NAMESANDSTATE);
		}
		else if (node instanceof TCIdentifierToken)
		{
			TCIdentifierToken id = (TCIdentifierToken)node;
			return env.findType(new TCNameToken(id.getLocation(), fromModule, id.getName()), fromModule);
		}
		else if (node instanceof TCUnresolvedType)
		{
			TCUnresolvedType unresolved = (TCUnresolvedType)node;
			return env.findType(unresolved.typename, fromModule);
		}
		else if (node instanceof TCParameterType)
		{
			TCParameterType paramtype = (TCParameterType)node;
			return paramtype.getDefinition();
		}
		else if (node instanceof TCMkTypeExpression)
		{
			TCMkTypeExpression mk = (TCMkTypeExpression)node;
			return env.findType(mk.typename, fromModule);
		}
		else if (node instanceof TCFieldExpression)
		{
			TCFieldExpression fexp = (TCFieldExpression)node;
			
			if (fexp.root.isRecord(fexp.location))
			{
	    		TCRecordType rec = fexp.root.getRecord();

	    		if (rec.name.getName().equals("?"))		// union of records with same field tags
				{
					if (fexp.root.definitions != null)
					{
						return fexp.root.definitions.get(0);
					}
				}
				else
				{
		    		TCField field = rec.findField(fexp.field.getName());
		    		
		    		if (field != null)
		    		{
		    			return new TCLocalDefinition(field.tagname.getLocation(), field.tagname, field.type);
		    		}
				}
			}
			else if (fexp.root.isClass(env))
			{
	    		TCClassType cls = fexp.root.getClassType(env);
	    		return cls.findName(fexp.memberName, NameScope.VARSANDNAMES);
			}
		}
		else if (node instanceof TCIsExpression)
		{
			TCIsExpression isex = (TCIsExpression)node;
			return env.findType(isex.typename, fromModule);
		}
		else if (node instanceof TCNameToken)
		{
			TCNameToken name = (TCNameToken)node;
			TCClassDefinition classdef = env.findClassDefinition();

			if (classdef != null && classdef.getDefinitions() != null)	// eg. per => and mutex names
			{
				for (TCDefinition def: classdef.getDefinitions())
				{
					if (def.name != null && def.name.equals(name))
					{
						return def;
					}
				}
			}
			
			return env.findName(name, NameScope.VARSANDNAMES);
		}
		
		return null;
	}
}
