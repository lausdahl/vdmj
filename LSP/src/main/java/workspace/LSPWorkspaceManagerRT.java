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

import java.io.FilenameFilter;

import com.fujitsu.vdmj.Settings;
import com.fujitsu.vdmj.lex.Dialect;

public class LSPWorkspaceManagerRT extends LSPWorkspaceManagerPP
{
	public LSPWorkspaceManagerRT()
	{
		Settings.dialect = Dialect.VDM_RT;
	}
	
	@Override
	protected FilenameFilter getFilenameFilter()
	{
		return Dialect.VDM_RT.getFilter();
	}
	
	@Override
	protected String[] getFilenameFilters()
	{
		return new String[] { "**/*.vpp", "**/*.vdmrt" }; 
	}
}