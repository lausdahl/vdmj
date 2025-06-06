/*******************************************************************************
 *
 *	Copyright (c) 2024 Nick Battle.
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

package com.fujitsu.vdmj.pog;

import com.fujitsu.vdmj.lex.LexLocation;
import com.fujitsu.vdmj.tc.lex.TCNameList;
import com.fujitsu.vdmj.tc.lex.TCNameSet;
import com.fujitsu.vdmj.tc.lex.TCNameToken;

/**
 * A context to represent resolved ambiguous variables
 */
public class POResolveContext extends POContext
{
	private final TCNameSet variables;
	private final LexLocation location;
	
	public POResolveContext(TCNameList variables, LexLocation location)
	{
		this.variables = new TCNameSet();
		this.variables.addAll(variables);
		this.location = location;
	}
	
	public POResolveContext(TCNameToken var, LexLocation location)
	{
		this(new TCNameList(var), location);
	}
	
	@Override
	public TCNameSet resolvedVariables()
	{
		return variables;
	}

	@Override
	public String getSource()
	{
		return "-- Resolved ambiguity " + variables +
				" at " + location.startLine + ":" + location.startPos;
	}
}
