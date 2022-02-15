package org.idempiere.fa.process;

import java.util.ArrayList;

import org.compiere.model.MAssetDisposed;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.process.SvrProcess;
import org.compiere.util.Msg;

public class InvoiceCreateDisposal extends SvrProcess {

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
		ArrayList<MAssetDisposed> assetDisposeds = new ArrayList<MAssetDisposed>();
		/*
		 * //@win if (invoice.isA_Processed()) return
		 * "Invoice has been processed";
		 */
		if (!invoice.isSOTrx())
			return "Error: Not AR Invoice";

		if (!invoice.get_ValueAsBoolean("IsTrackAsAsset"))
			return "Error: Not Fixed Assets Invoice";

		MInvoiceLine[] lines = invoice.getLines(true);

		for (MInvoiceLine line : lines) {
			if (!line.get_ValueAsBoolean("IsTrackAsAsset"))
				continue;

			if (line.isA_Processed())
				continue;

			if (line.getA_Asset_ID() > 0) {
				MAssetDisposed assetDisposed = MAssetDisposed.createAssetDisposed(line);
				assetDisposed.saveEx();
				assetDisposeds.add(assetDisposed);
			}
			line.setA_Processed(true);
			line.saveEx();
		}

		// @win //invoice.setA_Processed(true);
		invoice.saveEx();

		// Add by Randy - Print All Message for Generated Disposal 
		for(MAssetDisposed dispose : assetDisposeds){
			String message = Msg.parseTranslation(getCtx(), "@GeneratedAssetDisposal@ - " + dispose.getDocumentNo());
			addBufferLog(0, null, null, message, dispose.get_Table_ID(),
					dispose.getA_Asset_Disposed_ID());
		}
		//Randy
		return "";
	}

}