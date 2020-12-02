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

package com.fujitsu.vdmj.in.statements;

import com.fujitsu.vdmj.in.statements.visitors.INStatementVisitor;
import com.fujitsu.vdmj.lex.LexLocation;
import com.fujitsu.vdmj.runtime.Context;
import com.fujitsu.vdmj.values.Value;
import com.fujitsu.vdmj.values.VoidValue;

public class INSkipStatement extends INStatement
{
	private static final long serialVersionUID = 1L;

	public INSkipStatement(LexLocation location)
	{
		super(location);
	}

	@Override
	public String toString()
	{
		return "skip";
	}

	@Override
	public Value eval(Context ctxt)
	{
		breakpoint.check(location, ctxt);
		return new VoidValue();
	}

	@Override
	public <R, S> R apply(INStatementVisitor<R, S> visitor, S arg)
	{
		return visitor.caseSkipStatement(this, arg);
	}
}