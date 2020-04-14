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

package com.fujitsu.vdmj.ast.statements;

import com.fujitsu.vdmj.ast.expressions.ASTExpression;
import com.fujitsu.vdmj.lex.LexLocation;

public class ASTAssignmentStatement extends ASTStatement
{
	private static final long serialVersionUID = 1L;

	public final ASTExpression exp;
	public final ASTStateDesignator target;

	public ASTAssignmentStatement(
		LexLocation location, ASTStateDesignator target, ASTExpression exp)
	{
		super(location);
		this.exp = exp;
		this.target = target;
	}

	@Override
	public String toString()
	{
		return target + " := " + exp;
	}

	@Override
	public String kind()
	{
		return "assignment";
	}

	@Override
	public <R, S> R apply(ASTStatementVisitor<R, S> visitor, S arg)
	{
		return visitor.caseAssignmentStatement(this, arg);
	}
}
