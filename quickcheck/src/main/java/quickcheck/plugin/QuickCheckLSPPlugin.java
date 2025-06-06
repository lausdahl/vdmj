/*******************************************************************************
 *
 *	Copyright (c) 2023 Nick Battle.
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
 *	SPDX-License-Identifier: GPL-3.0-or-later
 *
 ******************************************************************************/

package quickcheck.plugin;

import java.util.List;
import java.util.Vector;

import com.fujitsu.vdmj.lex.Dialect;
import com.fujitsu.vdmj.plugins.HelpList;
import com.fujitsu.vdmj.pog.ProofObligationList;
import com.fujitsu.vdmj.runtime.Interpreter;
import com.fujitsu.vdmj.util.Utils;

import json.JSONArray;
import json.JSONObject;
import lsp.CancellableThread;
import quickcheck.QuickCheck;
import quickcheck.commands.QCConsole;
import quickcheck.commands.QCRunLSPCommand;
import quickcheck.commands.QuickCheckLSPCommand;
import rpc.RPCDispatcher;
import rpc.RPCErrors;
import rpc.RPCMessageList;
import rpc.RPCRequest;
import vdmj.commands.AnalysisCommand;
import workspace.Diag;
import workspace.PluginRegistry;
import workspace.plugins.AnalysisPlugin;
import workspace.plugins.DAPPlugin;
import workspace.plugins.POPlugin;

public class QuickCheckLSPPlugin extends AnalysisPlugin
{
	public static AnalysisPlugin factory(Dialect dialect)
	{
		return new QuickCheckLSPPlugin();
	}
	
	@Override
	public String getName()
	{
		return "QC";
	}

	@Override
	public void init()
	{
		// Register handler with RPCDispatcher
		RPCDispatcher dispatcher = RPCDispatcher.getInstance();
		dispatcher.register(new QuickCheckHandler(), "slsp/POG/quickcheck");
	}
	
	public RPCMessageList quickCheck(RPCRequest request)
	{
		if (messagehub.hasErrors())
		{
			Diag.error("Spec has errors");
			return new RPCMessageList(request, RPCErrors.InternalError, "Spec has errors");
		}
		else if (CancellableThread.currentlyRunning() != null)
		{
			Diag.error("Running " + CancellableThread.currentlyRunning());
			return new RPCMessageList(request, RPCErrors.InternalError, "Running " + CancellableThread.currentlyRunning());
		}
		
		DAPPlugin manager = DAPPlugin.getInstance();
		manager.refreshInterpreter();
		Interpreter interpreter = manager.getInterpreter();
		
		if (interpreter.getInitialContext() == null)	// eg. from unit tests
		{
			try
			{
				interpreter.init();
			}
			catch (Exception e)
			{
				Diag.error(e);
				return new RPCMessageList(request, RPCErrors.InternalError, "Init has errors");
			}
		}
		
		QuickCheck qc = new QuickCheck();
		
		QCConsole.setQuiet(true);
		QCConsole.setVerbose(false);

		qc.loadStrategies(getStrategyArgs(request));
		
		if (qc.hasErrors())
		{
			Diag.error("Failed to load QC strategies");
			return new RPCMessageList(request, RPCErrors.InternalError, "Failed to load QC strategies");
		}

		Vector<Integer> poList = new Vector<Integer>();
		Vector<String> poNames = new Vector<String>();
		long timeout = getParamArgs(request, poList, poNames);

		POPlugin pog = PluginRegistry.getInstance().getPlugin("PO");
		ProofObligationList all = pog.getProofObligations();
		ProofObligationList chosen = qc.getPOs(all, poList, poNames);
		
		if (qc.hasErrors())
		{
			Diag.error("Failed to find POs");
			return new RPCMessageList(request, RPCErrors.InternalError, "Failed to find POs");
		}
		else if (chosen.isEmpty())
		{
			Diag.error("No POs in scope");
			return new RPCMessageList(request, RPCErrors.InternalError, "No POs in scope");
		}
		else if (qc.initStrategies())
		{
			QuickCheckThread executor = new QuickCheckThread(request, qc, chosen, timeout);
			executor.start();
			return null;
		}
		else
		{
			Diag.error("No strategy to run");
			return new RPCMessageList(request, RPCErrors.InternalError, "No strategy to run");
		}
	}

	private long getParamArgs(RPCRequest request,
			Vector<Integer> poList, Vector<String> poNames)
	{
		long timeout = 1000L;	// faster default for GUI?
		
		if (request.get("params") != null)
		{
			JSONObject params = request.get("params");
			
			if (params.get("config") != null)
			{
				JSONObject config = params.get("config");
				JSONArray obligations = config.get("obligations");
				
				timeout = config.get("timeout", 1000L);
				
				if (obligations != null && !obligations.isEmpty())
				{
					for (int i=0; i < obligations.size(); i++)
					{
						long po = obligations.index(i);
						poList.add((int) po);
					}
				}
				else
				{
					poNames.add(".*");
				}
			}
		}
		
		return timeout;
	}

	private List<String> getStrategyArgs(RPCRequest request)
	{
		List<String> list = new Vector<String>();
		JSONObject params = request.get("params");
		
		if (params != null && params.get("strategies") != null)
		{
			JSONArray strategies = params.get("strategies");
			
			for (int i=0; i<strategies.size(); i++)
			{
				JSONObject sentry = strategies.index(i);
				String sname = sentry.get("name");
				Boolean enabled = sentry.get("enabled", true);
				
				if (sname != null && enabled)
				{
					list.add("-s");
					list.add(sname);	// NB. Only enable those listed
					
					for (String key: sentry.keySet())
					{
						Object value = sentry.get(key);
						
						switch (key)
						{
							case "name":
							case "enabled":
								break;
						
							default:
								// simulate "-fixed:size 100" etc.
								list.add("-" + sname + ":" + key);
								list.add(value.toString());
						}
					}
				}
			}
		}
		
		return list;
	}

	@Override
	public void setServerCapabilities(JSONObject capabilities)
	{
		JSONObject provider = capabilities.getPath("experimental.proofObligationProvider");
		
		if (provider != null)
		{
			provider.put("quickCheckProvider", true);
		}
	}
	
	@Override
	public AnalysisCommand getCommand(String line)
	{
		String[] argv = Utils.toArgv(line);
		
		switch (argv[0])
		{
			case "quickcheck":
			case "qc":
				return new QuickCheckLSPCommand(line);
				
			case "qcrun":
			case "qr":
				return new QCRunLSPCommand(line);
		}
		
		return null;
	}
	
	@Override
	public HelpList getCommandHelp()
	{
		return new HelpList(
			QuickCheckLSPCommand.SHORT + " - lightweight PO verification",
			QCRunLSPCommand.HELP);
	}
}
