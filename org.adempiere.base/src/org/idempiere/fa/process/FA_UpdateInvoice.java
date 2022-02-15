package org.idempiere.fa.process;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MProduct;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;

public class FA_UpdateInvoice extends SvrProcess {

	int C_Invoice_ID = 0;
	int C_InvoiceLine_ID = 0;
	int M_Inout_ID = 0;
	int M_InoutLine = 0;
	int C_Order_ID = 0;
	int C_OrderLine_ID = 0;
	int C_Charge_ID = 0;
	int M_Product_ID = 0;
	boolean isTrackAsAsset = false;

	@Override
	protected void prepare() {

	}

	@Override
	protected String doIt() throws Exception {

		C_Invoice_ID = getRecord_ID();

		if (C_Invoice_ID > 0) {

			MInvoice inv = new MInvoice(getCtx(), C_Invoice_ID, get_TrxName());

			isTrackAsAsset = inv.get_ValueAsBoolean("IsTrackAsAsset");

			StringBuilder sql = new StringBuilder();
			sql.append("SELECT cil.C_Invoice_ID, cil.C_InvoiceLine_ID, mi.M_InOut_ID, cil.M_InOutLine_ID");
			sql.append(" FROM M_MatchInv mm");
			sql.append(" INNER JOIN C_InvoiceLine cil ON cil.C_InvoiceLine_ID = mm.C_InvoiceLine_ID ");
			sql.append(" INNER JOIN M_InOutLine mil ON mil.M_InOutLine_ID = mm.M_InOutLine_ID ");
			sql.append(" INNER JOIN M_InOut mi ON mi.M_InOut_ID = mil.M_InOut_ID ");
			sql.append(" WHERE mm.C_InvoiceLine_ID IN ");
			sql.append(" (SELECT C_InvoiceLine_ID FROM C_InvoiceLine WHERE C_Invoice_ID = ?) ");


			PreparedStatement pstmt = null;
			ResultSet rs = null;
			try {
				pstmt = DB.prepareStatement(sql.toString(), null);
				pstmt.setInt(1, C_Invoice_ID);
				rs = pstmt.executeQuery();
				while (rs.next()) {
					C_Invoice_ID = rs.getInt(1);
					C_InvoiceLine_ID = rs.getInt(2);
					M_Inout_ID = rs.getInt(3);
					M_InoutLine = rs.getInt(4);


					
					MInvoiceLine invLine = new MInvoiceLine(getCtx(),C_InvoiceLine_ID, get_TrxName());
					M_Product_ID = invLine.getM_Product_ID();
					C_Charge_ID = invLine.getC_Charge_ID();
					MProduct prod = new MProduct(getCtx(), M_Product_ID,get_TrxName());
					String prodtype = prod.getProductType();

					if (C_Charge_ID > 0 && M_Product_ID == 0) {
						if (!inv.get_ValueAsBoolean("IsTrackAsAsset")){		
							inv.set_ValueOfColumn("IsTrackAsAsset", true);
							inv.saveEx();
						}
						
						invLine.set_ValueOfColumn("IsTrackAsAsset", true);
						invLine.saveEx();
						
						updateInOut(C_InvoiceLine_ID);

					} else if (M_Product_ID > 0 && C_Charge_ID == 0) {

						if (!prodtype.equals("I") && !prodtype.equals("R")) {

							if (!inv.get_ValueAsBoolean("IsTrackAsAsset")){		
								inv.set_ValueOfColumn("IsTrackAsAsset", true);
								inv.saveEx();
							}
							
							invLine.set_ValueOfColumn("IsTrackAsAsset", true);
							invLine.saveEx();
							
							updateInOut(C_InvoiceLine_ID);
							
						}

					}

				}

			} catch (Exception e) {
				throw new AdempiereException(e.toString());
			} finally {
				DB.close(rs, pstmt);
				rs = null;
				pstmt = null;
			}

		}

		return "";
	}

	
	public String updateInOut(int C_InvoiceLine_ID){
		
		StringBuilder sqlUpdate = new StringBuilder();
		sqlUpdate.append("UPDATE M_InOut SET isTrackAsAsset = 'Y' ");
		sqlUpdate.append(" WHERE M_InOut_ID = ?");
		sqlUpdate.append(" AND isTrackAsAsset = 'N' ");
		
		StringBuilder sqlUpdateLine = new StringBuilder();
		sqlUpdateLine.append("UPDATE M_InOutLine SET isTrackAsAsset = 'Y' ");
		sqlUpdateLine.append(" WHERE M_InOutLine_ID = ?");
		sqlUpdateLine.append(" AND isTrackAsAsset = 'N' ");
		
		StringBuilder sqlMatchInv = new StringBuilder();
		sqlMatchInv.append("SELECT mil.M_InOut_ID, mil.M_InOutLine_ID ");
		sqlMatchInv.append(" FROM M_MatchInv mm");
		sqlMatchInv.append(" INNER JOIN M_InOutLine mil ON mil.M_InOutLine_ID = mm.M_InOutLine_ID ");
		sqlMatchInv.append(" WHERE mm.C_InvoiceLine_ID = ? ");

		PreparedStatement pstmtMatchInv = null;
		ResultSet rsMatchInv = null;
		try {
			pstmtMatchInv = DB.prepareStatement(sqlMatchInv.toString(), null);
			pstmtMatchInv.setInt(1, C_Invoice_ID);
			rsMatchInv = pstmtMatchInv.executeQuery();
			while (rsMatchInv.next()){
				int M_InOut_ID = rsMatchInv.getInt(1);
				int M_InOutLine_ID = rsMatchInv.getInt(2);
				
				DB.executeUpdateEx(sqlUpdate.toString(), new Object[]{M_InOut_ID}, get_TrxName());
				DB.executeUpdateEx(sqlUpdateLine.toString(), new Object[]{M_InOutLine_ID}, get_TrxName());
				updatePO(M_InOutLine_ID);
				
			}			 
		} catch (Exception e) {
			return "Error";
		} finally {
			DB.close(rsMatchInv, pstmtMatchInv);
			rsMatchInv = null;
			pstmtMatchInv = null;
		}
		
		return "";
	}

	public String updatePO(int M_InOutLine_ID){
	
		StringBuilder sqlUpdate = new StringBuilder();
		sqlUpdate.append("UPDATE C_Order SET isTrackAsAsset = 'Y' ");
		sqlUpdate.append(" WHERE C_Order_ID = ?");
		sqlUpdate.append(" AND isTrackAsAsset = 'N' ");
		
		StringBuilder sqlUpdateLine = new StringBuilder();
		sqlUpdateLine.append("UPDATE C_OrderLine SET isTrackAsAsset = 'Y' ");
		sqlUpdateLine.append(" WHERE C_OrderLine_ID = ?");
		sqlUpdateLine.append(" AND isTrackAsAsset = 'N' ");
		
		StringBuilder sqlMatchPO = new StringBuilder();
		sqlMatchPO.append("SELECT cil.C_Order_ID, cil.C_OrderLine_ID ");
		sqlMatchPO.append(" FROM M_MatchPO mm");
		sqlMatchPO.append(" INNER JOIN C_OrderLine cil ON cil.C_OrderLine_ID = mm.C_OrderLine_ID ");
		sqlMatchPO.append(" WHERE mm.M_InOutLine_ID = ? ");
	
		PreparedStatement pstmtMatchPO = null;
		ResultSet rsMatchPO = null;
		try {
			pstmtMatchPO = DB.prepareStatement(sqlMatchPO.toString(), null);
			pstmtMatchPO.setInt(1, M_InOutLine_ID);
			rsMatchPO = pstmtMatchPO.executeQuery();
			while (rsMatchPO.next()){
				int C_Order_ID = rsMatchPO.getInt(1);
				int C_OrderLine_ID = rsMatchPO.getInt(2);
				
				DB.executeUpdateEx(sqlUpdate.toString(), new Object[]{C_Order_ID}, get_TrxName());
				DB.executeUpdateEx(sqlUpdateLine.toString(), new Object[]{C_OrderLine_ID}, get_TrxName());
				
				
			}			 
		} catch (Exception e) {
			return "Error";
		} finally {
			DB.close(rsMatchPO, pstmtMatchPO);
			rsMatchPO = null;
			pstmtMatchPO = null;
		}
		
		return"";
	
	} 
	
}
