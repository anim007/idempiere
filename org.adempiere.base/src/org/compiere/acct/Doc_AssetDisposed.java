package org.compiere.acct;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;

import org.compiere.model.MAccount;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MAssetAcct;
import org.compiere.model.MAssetDisposed;
import org.compiere.model.MCharge;
import org.compiere.model.MDocType;
import org.compiere.model.MFADefaultAccount;
import org.compiere.util.Env;

/**
 * @author Teo_Sarca, SC ARHIPAC SERVICE SRL
 */
public class Doc_AssetDisposed extends Doc {
	/**
	 * @param ass
	 * @param clazz
	 * @param rs
	 * @param defaultDocumentType
	 * @param trxName
	 */
	public Doc_AssetDisposed(MAcctSchema as, ResultSet rs, String trxName) {
		super(as, MAssetDisposed.class, rs, MDocType.DOCBASETYPE_FixedAssetsDisposal, trxName);
	}

	protected String loadDocumentDetails() {
		return null;
	}

	public BigDecimal getBalance() {
		return Env.ZERO;
	}

	public ArrayList<Fact> createFacts(MAcctSchema as) {
		MAssetDisposed assetDisp = (MAssetDisposed) getPO();

		ArrayList<Fact> facts = new ArrayList<Fact>();
		Fact fact = new Fact(this, as, assetDisp.getPostingType());

		BigDecimal assetAmt = assetDisp.getA_Asset_Cost();
		BigDecimal accumDepAmt = assetDisp.getA_Accumulated_Depr();
		BigDecimal assetNetAmt = assetAmt.subtract(accumDepAmt);

		if (MAssetDisposed.A_DISPOSED_METHOD_Trade.equalsIgnoreCase(assetDisp.getA_Disposed_Method())) {
			BigDecimal amt = assetDisp.getC_InvoiceLine().getLineNetAmt();

			fact.createLine(null, MFADefaultAccount.getAssetRevenueAccount(as), as.getC_Currency_ID(), amt, Env.ZERO);

			fact.createLine(null, getAccount(MAssetAcct.COLUMNNAME_A_Accumdepreciation_Acct, as), as.getC_Currency_ID(),
					accumDepAmt, Env.ZERO);

			if (amt.compareTo(assetNetAmt) < 0) {
				BigDecimal loss = assetNetAmt.subtract(amt);

				fact.createLine(null, getAccount(MAssetAcct.COLUMNNAME_A_Disposal_Loss_Acct, as), as.getC_Currency_ID(),
						loss, Env.ZERO);

			} else if (amt.compareTo(assetNetAmt) > 0) {
				BigDecimal gain = amt.subtract(assetNetAmt);

				fact.createLine(null, getAccount(MAssetAcct.COLUMNNAME_A_Disposal_Gain_Acct, as), as.getC_Currency_ID(),
						Env.ZERO, gain);
			}

			fact.createLine(null, getAccount(MAssetAcct.COLUMNNAME_A_Asset_Acct, as), as.getC_Currency_ID(), Env.ZERO,
					assetAmt);

		} else if (MAssetDisposed.A_DISPOSED_METHOD_Simple.equalsIgnoreCase(assetDisp.getA_Disposed_Method())) {
			fact.createLine(null, getAccount(MAssetAcct.COLUMNNAME_A_Asset_Acct, as), as.getC_Currency_ID(), Env.ZERO,
					assetAmt);
			fact.createLine(null, getAccount(MAssetAcct.COLUMNNAME_A_Accumdepreciation_Acct, as), as.getC_Currency_ID(),
					accumDepAmt, Env.ZERO);

			// @Randy Change Disposal Loss Account to Charge Account if charge is not null
			if (assetNetAmt.compareTo(Env.ZERO) > 0) {
				if (assetDisp.get_ValueAsInt("C_Charge_ID") > 0)
					fact.createLine(null, MCharge.getAccount(assetDisp.get_ValueAsInt("C_Charge_ID"), as),
							as.getC_Currency_ID(), assetNetAmt, Env.ZERO);
				else
					fact.createLine(null, getAccount(MAssetAcct.COLUMNNAME_A_Disposal_Loss_Acct, as), as.getC_Currency_ID(),
							assetNetAmt, Env.ZERO);
			}
		}

		facts.add(fact);

		return facts;
	}

	private MAccount getAccount(String accountName, MAcctSchema as) {
		MAssetDisposed assetDisp = (MAssetDisposed) getPO();
		MAssetAcct assetAcct = MAssetAcct.forA_Asset_ID(getCtx(), as.get_ID(), assetDisp.getA_Asset_ID(), assetDisp.getPostingType(),
				assetDisp.getDateAcct(), null);
		int account_id = (Integer) assetAcct.get_Value(accountName);
		return MAccount.get(getCtx(), account_id);
	}

}