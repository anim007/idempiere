/**
 * 
 */
package org.idempiere.fa.model;

import java.util.List;
import java.util.logging.Level;

import org.adempiere.exceptions.FillMandatoryException;
import org.compiere.acct.Fact;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MAssetAddition;
import org.compiere.model.MClient;
import org.compiere.model.MInOutLine;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.PO;
import org.compiere.process.DocAction;
import org.compiere.util.CLogger;
import org.idempiere.fa.exceptions.AssetInvoiceWithMixedLines_LRO;
import org.idempiere.fa.exceptions.AssetProductStockedException;

/**
 * Fixed Assets Model Validator
 * @author Teo_Sarca, SC ARHIPAC SERVICE SRL
 * @author edwinang, Taowi 
 *
 */
public class ModelValidator
implements org.compiere.model.ModelValidator, org.compiere.model.FactsValidator
{
	/** Logger */
	private static CLogger log = CLogger.getCLogger(ModelValidator.class);
	/** Client */
	private int m_AD_Client_ID = -1;

	public int getAD_Client_ID() {
		return m_AD_Client_ID;
	}

	public void initialize(ModelValidationEngine engine, MClient client)
	{
		if (client != null)
			m_AD_Client_ID = client.getAD_Client_ID();

		engine.addModelChange(MOrderLine.Table_Name, this);		
		engine.addModelChange(MInOutLine.Table_Name, this);		
		engine.addModelChange(MInvoiceLine.Table_Name, this);		
		engine.addDocValidate(MInvoice.Table_Name, this);

		//engine.addModelChange(MMatchInv.Table_Name, this);
		//
		//		engine.addFactsValidate(MDepreciationEntry.Table_Name, this);
	}

	public String login(int AD_Org_ID, int AD_Role_ID, int AD_User_ID)
	{
		return null;
	}

	public String modelChange(PO po, int type) throws Exception
	{
		if (po instanceof MOrderLine) {
			MOrderLine ol = (MOrderLine) po;
			checkAssetOrderLine(ol, type);

		}

		if (po instanceof MInOutLine) {
			MInOutLine il = (MInOutLine) po;
			checkAssetReceiptLine(il, type);

		}

		if (po instanceof MInvoiceLine) {
			MInvoiceLine il = (MInvoiceLine) po;
			checkAssetAPInvoiceLine(il, type);

		}

		return null;

	}

	public String docValidate(PO po, int timing)
	{

		if (log.isLoggable(Level.INFO)) log.info(po.get_TableName() + " Timing: " + timing);
		String result = null;

		// TABLE C_Invoice
		String tableName = po.get_TableName();
		if(tableName.equals(MInvoice.Table_Name)){
			// Invoice - Validate Fixed Assets Invoice (LRO)
			if (timing==TIMING_AFTER_PREPARE)
			{
				MInvoice invoice = (MInvoice)po;
				if (invoice.isTrackAsAsset() && !invoice.isSOTrx())
					validateFixedAssetsInvoice(invoice);
			} else if (timing==TIMING_AFTER_COMPLETE) {
				MInvoice invoice = (MInvoice)po;
				if (invoice.isTrackAsAsset() && !invoice.isSOTrx()) {
					createAssetExpenditureFromInvoice(invoice);
				}	
			}

		}

		return result;
	} // docValidate

	private void createAssetExpenditureFromInvoice(MInvoice invoice) {

		MInvoiceLine[] iLines = invoice.getLines();

		for (MInvoiceLine iLine: iLines) {
			if (iLine.getA_CapvsExp().equals(MInvoiceLine.A_CAPVSEXP_Expense) && iLine.getA_Asset_ID() > 0) {
				MAssetAddition assetAddition = new MAssetAddition(iLine);
				assetAddition.processIt(DocAction.ACTION_Complete);
				assetAddition.saveEx();
			}
		}
	}

	/**
	 * 
	 * @param ol
	 * @param changeType
	 */

	public static void checkAssetOrderLine(MOrderLine ol, int changeType) {
		//
		// Set Asset Related Fields:
		boolean checkUpdate = ol.is_ValueChanged(MOrderLine.COLUMNNAME_A_CreateAsset) ||
				ol.is_ValueChanged(MOrderLine.COLUMNNAME_M_Product_ID) ||
				ol.is_ValueChanged(MOrderLine.COLUMNNAME_A_CapvsExp) ||
				ol.is_ValueChanged(MOrderLine.COLUMNNAME_A_Asset_Group_ID) ||
				ol.is_ValueChanged(MOrderLine.COLUMNNAME_A_Asset_ID) ||
				ol.is_ValueChanged(MOrderLine.COLUMNNAME_IsTrackAsAsset)
				;

		if (TYPE_BEFORE_NEW == changeType || (TYPE_BEFORE_CHANGE == changeType && checkUpdate)) {

			boolean isSOTrx = ol.getC_Order().isSOTrx();
			boolean isAsset = ol.getC_Order().isTrackAsAsset();			 

			if (!isSOTrx && isAsset) {
				/*
				if (ol.isA_CreateAsset()) {
					if (!MOrderLine.A_CAPVSEXP_Capital.equals(ol.getA_CapvsExp()))
						throw new AssetException("Create New Asset must select Capital");

					if (ol.getA_Asset_Group_ID() <=0)
						throw new FillMandatoryException(MOrderLine.COLUMNNAME_A_Asset_Group_ID);

					if (ol.getA_Asset_ID() > 0)
						ol.setA_Asset_ID(0);
				} else {

					if (ol.getA_Asset_ID() <= 0) 
						throw new FillMandatoryException(MOrderLine.COLUMNNAME_A_Asset_ID);

					if (ol.getA_Asset_Group_ID() > 0)
						ol.setA_Asset_Group_ID(0);

				}
				 */
				// Check Amounts & Qty
				//if (ol.getLineNetAmt().signum() == 0) 
				//	throw new FillMandatoryException(MOrderLine.COLUMNNAME_QtyEntered, MOrderLine.COLUMNNAME_PriceEntered);//@win TODO: Error kalau harganya nol. harusnya ga disini codenya

				// Check Product - fixed assets products shouldn't be stocked (but inventory objects are allowed)
				MProduct product = ol.getProduct();
				if (product!= null && product.isStocked()) 
					throw new AssetProductStockedException(product);
				if (ol.getC_Order().isTrackAsAsset()!=ol.isTrackAsAsset()) {
					ol.setIsTrackAsAsset(ol.getC_Order().isTrackAsAsset());
				}
			}

		}
	}

	/**
	 * 
	 * @param il
	 * @param changeType
	 */

	public static void checkAssetReceiptLine(MInOutLine il, int changeType) {
		//
		// Set Asset Related Fields:
		boolean checkUpdate = il.is_ValueChanged(MInOutLine.COLUMNNAME_A_CreateAsset) || 
				il.is_ValueChanged(MInvoiceLine.COLUMNNAME_M_Product_ID) ||
				il.is_ValueChanged(MInOutLine.COLUMNNAME_A_Asset_Group_ID) ||
				il.is_ValueChanged(MInOutLine.COLUMNNAME_A_Asset_ID) ||
				il.is_ValueChanged(MInOutLine.COLUMNNAME_IsTrackAsAsset)
				;

		if (TYPE_BEFORE_NEW == changeType || (TYPE_BEFORE_CHANGE == changeType && checkUpdate)) {

			boolean isSOTrx = il.getM_InOut().isSOTrx();
			boolean isAsset = il.getM_InOut().isTrackAsAsset();

			if (!isSOTrx && isAsset) {
				/*
				if (il.isA_CreateAsset()) {
					if (il.getA_Asset_Group_ID() <=0)
						throw new FillMandatoryException(MInOutLine.COLUMNNAME_A_Asset_Group_ID);

					if (il.getA_Asset_ID() > 0)
						il.setA_Asset_ID(0);
				} else {
					if (il.getA_Asset_ID() <= 0) 
						throw new FillMandatoryException(MInOutLine.COLUMNNAME_A_Asset_ID);

					if (il.getA_Asset_Group_ID() > 0)
						il.setA_Asset_Group_ID(0);
				}
				 */
				// Check Product - fixed assets products shouldn't be stocked (but inventory objects are allowed)
				MProduct product = il.getProduct();
				if (product!= null && product.isStocked()) 
					throw new AssetProductStockedException(product);

				if (il.getM_InOut().isTrackAsAsset()!=il.isTrackAsAsset()) {
					il.setIsTrackAsAsset(il.getM_InOut().isTrackAsAsset());
				}
			}
		}
	}

	/**
	 * 
	 * @param il
	 * @param changeType
	 */

	public static void checkAssetAPInvoiceLine(MInvoiceLine il, int changeType) {
		//
		// Set Asset Related Fields:
		boolean checkUpdate = il.is_ValueChanged(MInvoiceLine.COLUMNNAME_A_CreateAsset) || 
				il.is_ValueChanged(MInvoiceLine.COLUMNNAME_A_CapvsExp) ||
				il.is_ValueChanged(MInvoiceLine.COLUMNNAME_M_Product_ID) ||
				il.is_ValueChanged(MInvoiceLine.COLUMNNAME_A_Asset_Group_ID) ||
				il.is_ValueChanged(MInvoiceLine.COLUMNNAME_A_Asset_ID) ||
				il.is_ValueChanged(MInvoiceLine.COLUMNNAME_IsTrackAsAsset)
				;

		if (TYPE_BEFORE_NEW == changeType || (TYPE_BEFORE_CHANGE == changeType && checkUpdate)) {

			boolean isSOTrx = il.getC_Invoice().isSOTrx();
			boolean isAsset = il.getC_Invoice().isTrackAsAsset();

			if (!isSOTrx && isAsset) {
				/*
				if (il.isA_CreateAsset()) {
					if (!MInvoiceLine.A_CAPVSEXP_Capital.equals(il.getA_CapvsExp()))
						throw new AssetException("Create New Asset must select Capital");

					if (il.getA_Asset_Group_ID() <=0)
						throw new FillMandatoryException(MInvoiceLine.COLUMNNAME_A_Asset_Group_ID);

					if (il.getA_Asset_ID() > 0)
						il.setA_Asset_ID(0);

				} else {
					if (!MInvoiceLine.A_CAPVSEXP_Expense.equals(il.getA_CapvsExp()))
						throw new AssetException("Existing Asset Must Use Expense");

					if (il.getA_Asset_ID() <= 0) 
						throw new FillMandatoryException(MInvoiceLine.COLUMNNAME_A_Asset_ID);

					if (il.getA_Asset_Group_ID() > 0)
						il.setA_Asset_Group_ID(0);
				}
				 */
				// Check Amounts & Qty
				if (il.getLineNetAmt().signum() == 0) 
					throw new FillMandatoryException(MInvoiceLine.COLUMNNAME_QtyEntered, MInvoiceLine.COLUMNNAME_PriceEntered);

				// Check Product - fixed assets products shouldn't be stocked (but inventory objects are allowed)
				MProduct product = il.getProduct();
				if (product!= null && product.isStocked()) 
					throw new AssetProductStockedException(product);

				if (il.getC_Invoice().isTrackAsAsset()!=il.isTrackAsAsset()) {
					il.setIsTrackAsAsset(il.getC_Invoice().isTrackAsAsset());
				}

				if (product.getProductType().equals(MProduct.PRODUCTTYPE_Asset) && product.getM_Product_Category().getA_Asset_Group_ID() > 0) {
					il.setA_CreateAsset(true);
					il.setA_CapvsExp(MInvoiceLine.A_CAPVSEXP_Capital);
					il.setA_Asset_Group_ID(product.getM_Product_Category().getA_Asset_Group_ID());
				} else {
					il.setA_CreateAsset(false);
					il.setA_Asset_Group_ID(0);
				}
			}
		}
	}


	/**
	 * Check if is a valid fixed asset related invoice (LRO)
	 * @param invoice
	 */
	private void validateFixedAssetsInvoice(MInvoice invoice)
	{
		
		boolean hasFixedAssetLines = false;
		boolean hasNormalLines = false;
		for (MInvoiceLine line : invoice.getLines())
		{
			if (line.isTrackAsAsset())
				hasFixedAssetLines = true;

			else if (line.getM_Product_ID() > 0)
			{
				MProduct product = MProduct.get(line.getCtx(), line.getM_Product_ID());
				if (product.isItem())
					hasNormalLines = true;
			}
			//
			// No mixed lines are allowed
			if (hasFixedAssetLines && hasNormalLines)
				throw new AssetInvoiceWithMixedLines_LRO();
			
			//@TommyAng
			// Check Amounts & Qty
			if (line.getLineNetAmt().signum() == 0) 
				throw new FillMandatoryException(MOrderLine.COLUMNNAME_QtyEntered, MOrderLine.COLUMNNAME_PriceEntered);


		}

	}




	public String factsValidate(MAcctSchema schema, List<Fact> facts, PO po) {
		// TODO: implement it
		return null;
	}
}
