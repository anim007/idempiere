package org.idempiere.fa.process;

import java.util.List;

import org.compiere.model.MAsset;
import org.compiere.model.MAssetAcct;
import org.compiere.model.MAssetGroup;
import org.compiere.model.MAssetGroupAcct;
import org.compiere.model.MFADefaultAccount;
import org.compiere.model.Query;
import org.compiere.process.SvrProcess;
import org.compiere.util.Env;

public class FA_CopyDefaultAcct extends SvrProcess {

	private int p_FA_DefaultAcct_ID = 0;
	@Override
	protected void prepare() {

		p_FA_DefaultAcct_ID = getRecord_ID();
	}

	@Override
	protected String doIt() throws Exception {

		MFADefaultAccount faDefaultAcct = new MFADefaultAccount(getCtx(), p_FA_DefaultAcct_ID, get_TrxName());
		MAssetGroup[] assetGroups = getAssetGroup();
		if (assetGroups.length > 0) {
			for (MAssetGroup assetGroup : assetGroups) {
				MAssetGroupAcct[] assetGroupAccts = getAssetGroupAcct(assetGroup.get_ID(), faDefaultAcct.getC_AcctSchema_ID());
				if (assetGroupAccts.length > 0) {
					for (MAssetGroupAcct assetGroupAcct: assetGroupAccts) {
						assetGroupAcct.setA_Depreciation_ID(faDefaultAcct.getA_Depreciation_ID());
						assetGroupAcct.setA_Depreciation_F_ID(faDefaultAcct.getA_Depreciation_ID());
						assetGroupAcct.setPostingType(faDefaultAcct.getPostingType());
						assetGroupAcct.setA_Asset_Acct(faDefaultAcct.getA_Asset_Acct());
						assetGroupAcct.setA_Depreciation_Acct(faDefaultAcct.getA_Depreciation_Acct());
						assetGroupAcct.setA_Accumdepreciation_Acct(faDefaultAcct.getA_Accumdepreciation_Acct());
						assetGroupAcct.setA_Disposal_Revenue_Acct(faDefaultAcct.getA_Disposal_Gain_Acct());
						assetGroupAcct.setA_Disposal_Loss_Acct(faDefaultAcct.getA_Disposal_Loss_Acct());
						assetGroupAcct.saveEx();
						
					}
				} else {
					MAssetGroupAcct assetGroupAcct = new MAssetGroupAcct(getCtx(), 0, get_TrxName());
					assetGroupAcct.setAD_Org_ID(faDefaultAcct.getAD_Org_ID());
					assetGroupAcct.setC_AcctSchema_ID(faDefaultAcct.getC_AcctSchema_ID());
					assetGroupAcct.setA_Asset_Group_ID(assetGroup.getA_Asset_Group_ID());
					assetGroupAcct.setA_Depreciation_ID(faDefaultAcct.getA_Depreciation_ID());
					assetGroupAcct.setA_Depreciation_F_ID(faDefaultAcct.getA_Depreciation_ID());
					assetGroupAcct.setPostingType(faDefaultAcct.getPostingType());
					assetGroupAcct.setA_Asset_Acct(faDefaultAcct.getA_Asset_Acct());
					assetGroupAcct.setA_Depreciation_Acct(faDefaultAcct.getA_Depreciation_Acct());
					assetGroupAcct.setA_Accumdepreciation_Acct(faDefaultAcct.getA_Accumdepreciation_Acct());
					assetGroupAcct.setA_Disposal_Revenue_Acct(faDefaultAcct.getA_Disposal_Gain_Acct());
					assetGroupAcct.setA_Disposal_Loss_Acct(faDefaultAcct.getA_Disposal_Loss_Acct());
					assetGroupAcct.saveEx();
					
				}
			}
		}
		MAsset[] assets = getAsset();
		if (assets.length > 0) {
			for (MAsset asset : assets) {
				MAssetAcct[] assetAccts = getAssetAcct(asset.get_ID(), faDefaultAcct.getC_AcctSchema_ID());
				if (assetAccts.length > 0) {
					for (MAssetAcct assetAcct: assetAccts) {
						assetAcct.setA_Depreciation_ID(faDefaultAcct.getA_Depreciation_ID());
						assetAcct.setA_Depreciation_F_ID(faDefaultAcct.getA_Depreciation_ID());
						assetAcct.setPostingType(faDefaultAcct.getPostingType());
						assetAcct.setA_Asset_Acct(faDefaultAcct.getA_Asset_Acct());
						assetAcct.setA_Depreciation_Acct(faDefaultAcct.getA_Depreciation_Acct());
						assetAcct.setA_Accumdepreciation_Acct(faDefaultAcct.getA_Accumdepreciation_Acct());
						assetAcct.setA_Disposal_Revenue_Acct(faDefaultAcct.getA_Disposal_Gain_Acct());
						assetAcct.setA_Disposal_Loss_Acct(faDefaultAcct.getA_Disposal_Loss_Acct());
						assetAcct.saveEx();
						
					}
				} else {
					MAssetAcct assetAcct = new MAssetAcct(getCtx(), 0, get_TrxName());
					assetAcct.setAD_Org_ID(faDefaultAcct.getAD_Org_ID());
					assetAcct.setC_AcctSchema_ID(faDefaultAcct.getC_AcctSchema_ID());
					assetAcct.setA_Asset_ID(asset.get_ID());
					assetAcct.setA_Depreciation_ID(faDefaultAcct.getA_Depreciation_ID());
					assetAcct.setPostingType(faDefaultAcct.getPostingType());
					assetAcct.setA_Asset_Acct(faDefaultAcct.getA_Asset_Acct());
					assetAcct.setA_Depreciation_Acct(faDefaultAcct.getA_Depreciation_Acct());
					assetAcct.setA_Accumdepreciation_Acct(faDefaultAcct.getA_Accumdepreciation_Acct());
					assetAcct.setA_Disposal_Revenue_Acct(faDefaultAcct.getA_Disposal_Gain_Acct());
					assetAcct.setA_Disposal_Loss_Acct(faDefaultAcct.getA_Disposal_Loss_Acct());
					assetAcct.saveEx();
					
				}
			}
		}
		return null;
	}

	private MAssetGroup[] getAssetGroup() {		
		List<MAssetGroup> list = new Query(getCtx(), MAssetGroup.Table_Name,"AD_Client_ID=?", get_TrxName())
		.setParameters(Env.getAD_Client_ID(getCtx()))
		.setOnlyActiveRecords(true)
		.list();

		MAssetGroup[] assetGroups = new MAssetGroup[list.size()];
		list.toArray(assetGroups);
		return assetGroups;

	}

	private MAsset[] getAsset() {		
		List<MAsset> list = new Query(getCtx(), MAsset.Table_Name,"AD_Client_ID=?", get_TrxName())
		.setParameters(Env.getAD_Client_ID(getCtx()))		
		.setOnlyActiveRecords(true)
		.list();

		MAsset[] assets = new MAsset[list.size()];
		list.toArray(assets);
		return assets;

	}

	private MAssetGroupAcct[] getAssetGroupAcct(int assetGroupID, int C_AcctSchema_ID) {		
		List<MAssetGroupAcct> list = new Query(getCtx(), MAssetGroupAcct.Table_Name,"A_Asset_Group_ID=? AND C_AcctSchema_ID=?", get_TrxName())
		.setParameters(new Object[]{assetGroupID, C_AcctSchema_ID})
		.setOnlyActiveRecords(true)
		.list();

		MAssetGroupAcct[] groupAcct = new MAssetGroupAcct[list.size()];
		list.toArray(groupAcct);
		return groupAcct;
	}

	private MAssetAcct[] getAssetAcct(int assetID, int C_AcctSchema_ID) {		
		List<MAssetAcct> list = new Query(getCtx(), MAssetAcct.Table_Name,"A_Asset_ID=? AND C_AcctSchema_ID=?", get_TrxName())
		.setParameters(new Object[]{assetID, C_AcctSchema_ID})
		.setOnlyActiveRecords(true)
		.list();

		MAssetAcct[] groupAcct = new MAssetAcct[list.size()];
		list.toArray(groupAcct);
		return groupAcct;
	}


}
