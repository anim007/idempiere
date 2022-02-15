package org.idempiere.fa.model;

import java.math.BigDecimal;
import java.util.Properties;

import org.adempiere.model.GridTabWrapper;
import org.compiere.model.CalloutEngine;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.I_A_Asset_Addition;
import org.compiere.model.MAssetAddition;
import org.compiere.model.MAssetGroup;
import org.compiere.model.MAssetGroupAcct;
import org.compiere.model.MConversionRateUtil;
import org.compiere.model.MProject;
import org.compiere.model.SetGetUtil;
import org.compiere.util.Env;
import org.compiere.util.TimeUtil;


/**
 * @author Teo Sarca, http://www.arhipac.ro
 */
public class CalloutA_Asset_Addition extends CalloutEngine
{
	public String matchInv(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value)
	{
		if (isCalloutActive() || value == null)
			return "";

		int M_MatchInv_ID = ((Number)value).intValue();
		if (M_MatchInv_ID > 0)
		{
			MAssetAddition.setM_MatchInv(SetGetUtil.wrap(mTab), M_MatchInv_ID);
		}
		//
		return amt(ctx, WindowNo, mTab, mField, value);
	}

	public String project(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value)
	{
		if (isCalloutActive())
			return "";
		//
		int project_id = 0;
		if (value != null && value instanceof Number)
			project_id = ((Number)value).intValue();
		else
			return "";
		//
		BigDecimal amt = Env.ZERO;
		if (project_id > 0) {
			MProject prj = new MProject(ctx, project_id, null);
			amt = prj.getProjectBalanceAmt();
			mTab.setValue(MAssetAddition.COLUMNNAME_C_Currency_ID, prj.getC_Currency_ID());
		}
		mTab.setValue(MAssetAddition.COLUMNNAME_AssetAmtEntered, amt);
		return amt(ctx, WindowNo, mTab, mField, value);
	}

	public String amt(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value)
	{
		if (isCalloutActive())
			return "";
		//
		String columnName = mField.getColumnName();
		if (MAssetAddition.COLUMNNAME_A_Accumulated_Depr.equals(columnName))
		{
			mTab.setValue(MAssetAddition.COLUMNNAME_A_Accumulated_Depr_F, value);
		}
		else
		{
			BigDecimal amtEntered = (BigDecimal) mTab.getValue(MAssetAddition.COLUMNNAME_AssetAmtEntered);
			mTab.setValue(MAssetAddition.COLUMNNAME_AssetSourceAmt, amtEntered);
			MConversionRateUtil.convertBase(SetGetUtil.wrap(mTab),
					MAssetAddition.COLUMNNAME_DateAcct,
					MAssetAddition.COLUMNNAME_AssetAmtEntered,
					MAssetAddition.COLUMNNAME_AssetValueAmt,
					mField.getColumnName());
		}
		//
		return "";
	}

	public String dateDoc(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value)
	{
		if (isCalloutActive() || value == null)
			return "";
		
		mTab.setValue(MAssetAddition.COLUMNNAME_DateAcct, value);
		return "";
	}
	
	public String uselife(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value)
	{
		if (MAssetAddition.COLUMNNAME_DeltaUseLifeYears.equals(mField.getColumnName()))
		{
			mTab.setValue(MAssetAddition.COLUMNNAME_DeltaUseLifeYears_F, value);
		}
		return "";
	}
	
	public String newActivation (Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value){
		I_A_Asset_Addition assetAdd = GridTabWrapper.create(mTab, I_A_Asset_Addition.class);
		if(assetAdd.isA_CreateAsset())
			assetAdd.setIsAdjustUseLife(true);
		
		return null;
	}
	
	public String assetGroup (Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value){
		if (value==null)
			return "";
		
		MAssetGroupAcct assetGroupAcct = MAssetGroupAcct.forA_Asset_Group_ID(ctx, 
				(Integer) value, MAssetGroupAcct.POSTINGTYPE_Actual);

		mTab.setValue(MAssetAddition.COLUMNNAME_UseLifeYears, assetGroupAcct.getUseLifeYears());
		mTab.setValue(MAssetAddition.COLUMNNAME_A_Salvage_Value, assetGroupAcct.getA_Salvage_Value());
		
		return null;
	}
	
	public String periodOffset(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value)
	{
		if(value == null)
			return null;
		
		boolean isAdjustAccmDepr = (boolean) value;
		if(!isAdjustAccmDepr){
			mTab.setValue(MAssetAddition.COLUMNNAME_A_Accumulated_Depr, Env.ZERO);
			mTab.setValue(MAssetAddition.COLUMNNAME_A_Period_Start, Env.ONE);
		}
		
		I_A_Asset_Addition aa = GridTabWrapper.create(mTab, I_A_Asset_Addition.class);
		if (!aa.isAdjustAccmDepr())
		{
			return "";
		}
		
		int periods = TimeUtil.getMonthsBetween(aa.getDateDoc(), aa.getDateAcct());
		if (periods <= 0)
		{
			return "";
		}
		
		int uselifeMonths = aa.getDeltaUseLifeYears() * 12;
		if (uselifeMonths == 0)
		{
			return "";
		}
		double monthlyExpenseSL = aa.getAssetValueAmt().doubleValue() / uselifeMonths * periods;
		
		aa.setA_Period_Start(periods + 1);
		aa.setA_Accumulated_Depr(BigDecimal.valueOf(monthlyExpenseSL));
		aa.setA_Accumulated_Depr_F(BigDecimal.valueOf(monthlyExpenseSL));
		
		return "";
	}
	
}
