/*******************************************************************************
 *
 *	Copyright (c) 2016 Fujitsu Services Ltd.
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

package com.fujitsu.vdmj.pog;

import com.fujitsu.vdmj.po.definitions.PODefinition;
import com.fujitsu.vdmj.po.definitions.PODefinitionList;
import com.fujitsu.vdmj.po.definitions.POExplicitFunctionDefinition;
import com.fujitsu.vdmj.po.definitions.POImplicitFunctionDefinition;
import com.fujitsu.vdmj.po.expressions.POApplyExpression;
import com.fujitsu.vdmj.po.patterns.POPatternList;
import com.fujitsu.vdmj.po.types.POPatternListTypePair;
import com.fujitsu.vdmj.tc.lex.TCNameToken;
import com.fujitsu.vdmj.tc.types.TCFunctionType;
import com.fujitsu.vdmj.tc.types.TCProductType;
import com.fujitsu.vdmj.util.Utils;

public class RecursiveObligation extends ProofObligation
{
	public RecursiveObligation(
		POExplicitFunctionDefinition def, POApplyExpression apply, POContextStack ctxt)
	{
		super(apply.location, POType.RECURSIVE, ctxt);
		int measureLexical = getLex(def.measureDef);

		String lhs = getLHS(def);
		String rhs = apply.getMeasureApply(def.measureName);

		value = ctxt.getObligation(greater(measureLexical, lhs, rhs));
	}

	public RecursiveObligation(
		POImplicitFunctionDefinition def, POApplyExpression apply, POContextStack ctxt)
	{
		super(def.location, POType.RECURSIVE, ctxt);
		int measureLexical = getLex(def.measureDef);
		
		String lhs = getLHS(def);
		String rhs = def.measureName.getName() + "(" + apply.args + ")";

		value = ctxt.getObligation(greater(measureLexical, lhs, rhs));
	}
	
	public RecursiveObligation(PODefinitionList defs, POApplyExpression apply, POContextStack ctxt)
	{
		super(defs.get(0).location, POType.RECURSIVE, ctxt);
		int measureLexical = getLex(getMeasureDef(defs.get(0)));
		
		String lhs = getLHS(defs.get(0));
		String rhs = getMeasureName(defs.get(1)) + "(" + apply.args + ")";

		value = ctxt.getObligation(greater(measureLexical, lhs, rhs));
	}
	
	private String getLHS(PODefinition def)
	{
		StringBuilder sb = new StringBuilder();
		
		if (def instanceof POExplicitFunctionDefinition)
		{
			POExplicitFunctionDefinition edef = (POExplicitFunctionDefinition)def;
			sb.append(getMeasureName(edef));
			
			if (edef.typeParams != null)
			{
				sb.append("[");
				
				for (TCNameToken type: edef.typeParams)
				{
					sb.append("@");
					sb.append(type);
				}
				
				sb.append("]");
			}
			
			String sep = "";
			sb.append("(");
			
			for (POPatternList plist: edef.paramPatternList)
			{
				 sb.append(sep);
				 sb.append(Utils.listToString(plist));
				 sep = ", ";
			}

			sb.append(")");
		}
		else if (def instanceof POImplicitFunctionDefinition)
		{
			POImplicitFunctionDefinition idef = (POImplicitFunctionDefinition)def;
			sb.append(getMeasureName(idef));
			sb.append("(");

			for (POPatternListTypePair pltp: idef.parameterPatterns)
			{
				sb.append(pltp.patterns);
			}

			sb.append(")");
		}
		
		return sb.toString();
	}
	
	private String getMeasureName(PODefinition def)
	{
		if (def instanceof POExplicitFunctionDefinition)
		{
			POExplicitFunctionDefinition edef = (POExplicitFunctionDefinition)def;
			
			if (edef.measureName != null)
			{
				return edef.measureName.getName();
			}
			else
			{
				return "measure_" + edef.name.getName();
			}
		}
		else if (def instanceof POImplicitFunctionDefinition)
		{
			POImplicitFunctionDefinition idef = (POImplicitFunctionDefinition)def;
			
			if (idef.measureName != null)
			{
				return idef.measureName.getName();
			}
			else
			{
				return "measure_" + idef.name.getName();
			}
		}
		else
		{
			return null;
		}
	}

	private POExplicitFunctionDefinition getMeasureDef(PODefinition def)
	{
		if (def instanceof POExplicitFunctionDefinition)
		{
			POExplicitFunctionDefinition edef = (POExplicitFunctionDefinition)def;
			return edef.measureDef;
		}
		else if (def instanceof POImplicitFunctionDefinition)
		{
			POImplicitFunctionDefinition idef = (POImplicitFunctionDefinition)def;
			return idef.measureDef;
		}
		else
		{
			return null;
		}
	}

	private int getLex(POExplicitFunctionDefinition mdef)
	{
		if (mdef == null)
		{
			return 0;
		}
		
		TCFunctionType ftype = (TCFunctionType) mdef.getType();
		
		if (ftype.result instanceof TCProductType)
		{
			TCProductType type = (TCProductType)ftype.result;
			return type.types.size();
		}
		else
		{
			return 0;
		}
	}

	private String greater(int lexical, String lhs, String rhs)
	{
		if (lexical > 0)
		{
			StringBuilder sb = new StringBuilder();
			
			sb.append("(let lhs = ");
			sb.append(lhs);
			sb.append(", rhs = ");
			sb.append(rhs);
			sb.append(" in\n  ");
			
			String kw = "if";
			
			for (int i=1; i < lexical; i++)
			{
				sb.append(String.format("%s lhs.#%d <> rhs.#%d then lhs.#%d > rhs.#%d ", kw, i, i, i, i)); 
				kw = "elseif";
			}
			
			sb.append(String.format("else lhs.#%d > rhs.#%d)", lexical, lexical));
			
			return sb.toString();
		}
		else
		{
			return lhs + " > " + rhs;
		}
	}
}