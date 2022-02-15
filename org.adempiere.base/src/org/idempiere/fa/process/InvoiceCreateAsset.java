package org.idempiere.fa.process;

import java.util.ArrayList;

import org.compiere.model.MAssetAddition;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MMatchInv;
import org.compiere.process.SvrProcess;
import org.compiere.util.Env;
import org.compiere.util.Msg;

public class InvoiceCreateAsset extends SvrProcess {

	private int p_C_Invoice_ID = 0;
	@Override
	protected void prepare() {

		p_C_Invoice_ID = getRecord_ID();
	}

	@Override
	protected String doIt() throws Exception {

		if (p_C_Invoice_ID <= 0)
			return "Error: No Invoice selected";

		MInvoice invoice = new MInvoice(getCtx(), p_C_Invoice_ID, get_TrxName());
		/*
		if (invoice.isA_Processed())
			return "Invoice has been processed";
		 */
		if (invoice.isSOTrx() )
			return "Error: Not AP Invoice";

		if (!invoice.isTrackAsAsset())
			return "Error: Not Fixed Assets Invoice";

		MInvoiceLine[] lines = invoice.getLines(true);

		ArrayList<MAssetAddition> assetAdditions = new ArrayList<MAssetAddition>();
		
		for (MInvoiceLine line : lines) {
			if (!line.isTrackAsAsset() || !line.isA_CreateAsset() || line.isA_Processed())
				continue;

			if (line.getA_Asset_Group_ID()<=0 && !line.getA_CapvsExp().equalsIgnoreCase(MInvoiceLine.A_CAPVSEXP_Capital))
				return "Cannot Create New Asset, Missing Asset Group or Not Capital Type";

			MMatchInv[] matches = line.getMatchInvoice();

			for (MMatchInv match: matches) {
				boolean isQtyOnePerUOM = line.getA_Asset_Group().isOneAssetPerUOM();
				
				if (isQtyOnePerUOM) {
					int qty = match.getQty().intValue();

					for (int i=1;i<=qty;i++) {
						MAssetAddition assetAdd = MAssetAddition.createAsset(match);
						assetAdd.setA_QTY_Current(Env.ONE);
						assetAdd.setAssetAmtEntered(line.getPriceActual());
						assetAdd.setAssetSourceAmt(line.getPriceActual());
						//assetAdd.setAssetValueAmt(line.getPriceActual());
						assetAdd.saveEx();
						assetAdditions.add(assetAdd);
					}
				}
				else {
					MAssetAddition assetAdd = MAssetAddition.createAsset(match);
					assetAdd.setA_QTY_Current(line.getQtyInvoiced());
					assetAdd.saveEx();
					assetAdditions.add(assetAdd);
				}
				
			}
			
			line.setA_Processed(true);
			line.saveEx();


			/*
			else {
				MAssetAddition assetAdd = new MAssetAddition(line);
				assetAdd.setAssetAmtEntered(line.getLineNetAmt());
				assetAdd.setAssetSourceAmt(line.getLineNetAmt());
				assetAdd.saveEx();
			}
			 */
		}

		//@win //invoice.setA_Processed(true);
		invoice.saveEx();

		if (assetAdditions.isEmpty())
			return "No Asset Addition Generated";
		else {
			
			for (MAssetAddition assetAdd : assetAdditions) {
				String message = Msg.parseTranslation(getCtx(), "@GeneratedAssetAddition@ - " + assetAdd.getDocumentNo());
				addBufferLog(0, null, null, message, assetAdd.get_Table_ID(),
						assetAdd.getA_Asset_Addition_ID());
			}
			
		}
		


		return "";
	}

}