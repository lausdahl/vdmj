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

package com.fujitsu.vdmj.ast.expressions;

import com.fujitsu.vdmj.lex.LexLocation;
import com.fujitsu.vdmj.util.Utils;

public class ASTMuExpression extends ASTExpression
{
	private static final long serialVersionUID = 1L;
	public final ASTExpression record;
	public final ASTRecordModifierList modifiers;

	public ASTMuExpression(LexLocation location,
		ASTExpression record, ASTRecordModifierList modifiers)
	{
		super(location);
		this.record = record;
		this.modifiers = modifiers;
	}

	@Override
	public String toString()
	{
		return "mu(" + record + ", " + Utils.listToString(modifiers) + ")";
	}

	@Override
	public String kind()
	{
		return "mu";
	}

	@Override
	public <R, S> R apply(ASTExpressionVisitor<R, S> visitor, S arg)
	{
		return visitor.caseMuExpression(this, arg);
	}
}
