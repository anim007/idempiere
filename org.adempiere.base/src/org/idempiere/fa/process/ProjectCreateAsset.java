/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.idempiere.fa.process;


import java.sql.Timestamp;
import java.util.logging.Level;

import org.compiere.model.MAssetAddition;
import org.compiere.model.MAssetGroup;
import org.compiere.model.MProject;
import org.compiere.process.DocAction;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.Msg;

 
/**
 *  Open Project.
 *  Opening project will automatically create asset and asset addition
 *
 *	@author zuhri utama
 */
public class ProjectCreateAsset extends SvrProcess
{
	/**	Project 			*/
	private int 		m_C_Project_ID = 0;
	
	/**	Asset Group			*/
	private int 		m_AssetGroup_ID = 0;
	
	/**	Asset			*/
	//private int 		m_Asset_ID = 0;
	
	
	/** DateTrx for create asset	*/
	private Timestamp	m_DateTrx = null;
	
	//private String message = "";
	
	/**
	 *  Prepare - e.g., get Parameters.
	 */
	protected void prepare()
	{
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++)
		{
			String name = para[i].getParameterName();
			if (para[i].getParameter() == null)
				;
		
			else if (para[i].getParameterName().equalsIgnoreCase("A_Asset_Group_ID")) {
				m_AssetGroup_ID = para[i].getParameterAsInt();
			}
			/* @win temporary comment: currently we only allow addition from project. adjustment will be supported later
			else if (para[i].getParameterName().equalsIgnoreCase("A_Asset_ID")) {
				m_Asset_ID = para[i].getParameterAsInt();
			}
			*/
			else if (para[i].getParameterName().equalsIgnoreCase("DateTrx")) {
				m_DateTrx = (Timestamp)para[i].getParameter();
			}
			else {
				log.log(Level.SEVERE, "prepare - Unknown Parameter: " + name);
			}
		}
		
	}	//	prepare

	/**
	 *  Perform process.
	 *  @return Message (translated text)
	 *  @throws Exception if not successful
	 */
	protected String doIt() throws Exception
	{
		m_C_Project_ID = getRecord_ID();
		
		/* @win temporary comment: currently we only allow addition from project. adjustment will be supported later
		MAsset asset = null;
		
		if (m_Asset_ID > 0) {
			asset = new MAsset(getCtx(), m_Asset_ID, get_TrxName());
			m_AssetGroup_ID = asset.getA_Asset_Group_ID();
		}
		*/
		
		if (m_C_Project_ID == 0 || m_AssetGroup_ID == 0)
			return "Missing Mandatory Field Value (Project / Asset Group)";
		
		MProject project = new MProject (getCtx(), m_C_Project_ID, get_TrxName());
		
		if (!project.isProcessed())
			return "Error: only closed project can be capitalize";
		
		if (project.get_ValueAsBoolean("IsCapitalized"))
			return "Error: project has been capitalized";
					
					
		if (log.isLoggable(Level.INFO)) log.info("doIt - " + project);
		
		
		MAssetGroup assetGroup = new MAssetGroup(getCtx(), m_AssetGroup_ID, get_TrxName());

		MAssetAddition assetAdd = MAssetAddition.createAsset(project, assetGroup, true, m_DateTrx, m_DateTrx);
		//assetAdd.saveEx();
		
		if (!assetAdd.processIt(DocAction.ACTION_Complete)) {
			return "Error Process Asset Addition";
		}
		assetAdd.saveEx();
		
		project.set_ValueOfColumn("IsCapitalized", true);;
		project.saveEx();
		
		//message += ". @A_Asset_Addition_ID@ - " + assetAdd;
		
		String message = Msg.parseTranslation(getCtx(), "@GeneratedAssetAddition@ " + assetAdd.getDocumentNo());
		addBufferLog(0, null, null, message, assetAdd.get_Table_ID(), assetAdd.getA_Asset_Addition_ID());
		
		return "";
	}	//	doIt

}	//	ProjectClose
