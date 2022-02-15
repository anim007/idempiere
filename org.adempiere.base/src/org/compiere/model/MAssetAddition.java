package org.compiere.model;

import java.io.File;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.exceptions.FillMandatoryException;
import org.compiere.process.DocAction;
import org.compiere.process.DocOptions;
import org.compiere.process.DocumentEngine;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Trx;
import org.idempiere.fa.exceptions.AssetAlreadyDepreciatedException;
import org.idempiere.fa.exceptions.AssetException;
import org.idempiere.fa.exceptions.AssetNotImplementedException;
import org.idempiere.fa.util.POCacheLocal;


/**
 *  Asset Addition Model
 *	@author Teo Sarca
 *
 */
public class MAssetAddition extends X_A_Asset_Addition implements DocAction, DocOptions
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 5977180589101094202L;

	/** Static Logger */
	private static CLogger s_log = CLogger.getCLogger(MAssetAddition.class);

	private final static String ADDITION_TYPE_ADDITION = "Addition";
	private final static String ADDITION_TYPE_ADJUSTMENT = "Adjustment";
	private final static String ADDITION_TYPE_EXPENDITURE = "Expenditure";


	public MAssetAddition (Properties ctx, int A_Asset_Addition_ID, String trxName) {
		super (ctx, A_Asset_Addition_ID, trxName);

	}	

	public MAssetAddition (Properties ctx, ResultSet rs, String trxName) {
		super (ctx, rs, trxName);
	}	


	protected boolean beforeSave (boolean newRecord)
	{
		if (getC_Currency_ID() <= 0) {
			throw new FillMandatoryException(COLUMNNAME_C_Currency_ID);
		}

		if (getC_ConversionType_ID() <= 0 && getC_Currency_ID() != MClient.get(getCtx()).getAcctSchema().getC_Currency_ID()) {
			throw new FillMandatoryException(COLUMNNAME_C_ConversionType_ID);
		}

		getDateAcct();
		MConversionRateUtil.convertBase(SetGetUtil.wrap(this), COLUMNNAME_DateAcct, 
				COLUMNNAME_AssetAmtEntered, COLUMNNAME_AssetValueAmt, null);
		return true;
	}	//	beforeSave

	/**
	 * Create Asset and asset Addition from MMatchInv.
	 * MAssetAddition is saved.
	 * @param match match invoice
	 * @return asset addition
	 */
	public static MAssetAddition createAsset(MMatchInv match)
	{
		MAssetAddition assetAdd = new MAssetAddition(match);

		if (match.getC_InvoiceLine().getA_CapvsExp().equals(MAssetAddition.A_CAPVSEXP_Expense)) {
			//@win
			//TODO: make sure all columns are set
			//record expense to existing asset
			assetAdd.setA_Asset_ID(match.getC_InvoiceLine().getA_Asset_ID());
			assetAdd.setA_CreateAsset(false);
			assetAdd.setA_CapvsExp(MAssetAddition.A_CAPVSEXP_Expense);
			assetAdd.setAssetAmtEntered(match.getC_InvoiceLine().getLineNetAmt());


		} else {
			if (!match.getC_InvoiceLine().isA_CreateAsset()) {
				//second addition to existing asset
				//TODO: make sure all columns are set
				assetAdd.setA_Asset_ID(match.getC_InvoiceLine().getA_Asset_ID());
				assetAdd.setA_CreateAsset(false);
				assetAdd.setA_CapvsExp(MAssetAddition.A_CAPVSEXP_Capital);
				assetAdd.setAssetAmtEntered(match.getC_InvoiceLine().getLineNetAmt());

			} else {
				//create new asset
				//TODO: make sure all columns are set
				assetAdd.setA_Asset_Group_ID(match.getC_InvoiceLine().getA_Asset_Group_ID());
				assetAdd.setA_CreateAsset(true);
				assetAdd.setA_CapvsExp(MAssetAddition.A_CAPVSEXP_Capital);
				assetAdd.setAssetAmtEntered(match.getC_InvoiceLine().getLineNetAmt());
				assetAdd.setA_Period_Start(1);
				//assetAdd set use life years
				assetAdd.setA_QTY_Current(match.getC_InvoiceLine().getQtyInvoiced()); //TODO: Will have to split this to multi asset addition and asset for one asset per uom
				MAssetGroupAcct assetgrpacct = MAssetGroupAcct.forA_Asset_Group_ID(assetAdd.getCtx(), 
						match.getC_InvoiceLine().getA_Asset_Group_ID(), assetAdd.getPostingType());
				assetAdd.setUseLifeYears(assetgrpacct.getUseLifeYears());
			}
		}

		assetAdd.setDateDoc(match.getC_InvoiceLine().getC_Invoice().getDateInvoiced());
		assetAdd.setDateAcct(match.getC_InvoiceLine().getC_Invoice().getDateAcct());
		assetAdd.setIsApproved();
		assetAdd.saveEx();
		return assetAdd;
	}

	/**
	 * Create Asset and asset Addition from MProject. MAssetAddition is saved. 
	 * Addition from Project only allows initial addition (will definitely create new asset)
	 * @param	project
	 * @return asset addition
	 */
	public static MAssetAddition createAsset(MProject project, MAssetGroup assetGroup, 
			boolean isNewAddition, Timestamp dateDoc, Timestamp dateAcct)
	{
		MAssetAddition assetAdd = new MAssetAddition(project);
		//@win: currently assuming that project can only create new asset
		//@win: set asset group, set accumulated depreciation as zero, and set asset value, and asset qty
		//TODO: make sure all columns are set
		MAssetGroupAcct assetGroupAcct = MAssetGroupAcct.forA_Asset_Group_ID(project.getCtx(), assetGroup.get_ID(), POSTINGTYPE_Actual);
		assetAdd.setA_CreateAsset(isNewAddition);
		assetAdd.setIsAdjustUseLife(false);
		assetAdd.setIsAdjustAccmDepr(false);
		assetAdd.setUseLifeYears(assetGroupAcct.getUseLifeYears());
		assetAdd.setA_Accumulated_Depr(BigDecimal.ZERO);
		assetAdd.setA_Accumulated_Depr_F(BigDecimal.ZERO);

		assetAdd.setDateDoc(dateDoc);
		assetAdd.setDateAcct(dateAcct);
		assetAdd.saveEx();
		return assetAdd;
	}

	/**
	 * Construct addition from match invoice 
	 * @param match	match invoice model
	 */
	private MAssetAddition (MMatchInv match)
	{
		this(match.getCtx(), 0, match.get_TrxName());
		setAD_Org_ID(match.getAD_Org_ID());
		match.load(get_TrxName());
		setAD_Org_ID(match.getAD_Org_ID());
		setPostingType(POSTINGTYPE_Actual);
		setA_SourceType(A_SOURCETYPE_Invoice);
		setM_MatchInv_ID(match.get_ID());
		if (match.getC_InvoiceLine().getA_Asset_Group()!= null 
				&& match.getC_InvoiceLine().getA_CapvsExp().equals(MAssetAddition.A_CAPVSEXP_Capital)) {
			setA_CreateAsset(true);
			setA_Asset_Group_ID(match.getC_InvoiceLine().getA_Asset_Group_ID());
		} else {
			setA_CreateAsset(false);
		}
		setC_Invoice_ID(match.getC_InvoiceLine().getC_Invoice_ID());
		setC_InvoiceLine_ID(match.getC_InvoiceLine_ID());
		setM_InOutLine_ID(match.getM_InOutLine_ID());
		//setM_Product_ID(match.getM_Product_ID());
		//setM_AttributeSetInstance_ID(match.getM_AttributeSetInstance_ID());
		setA_QTY_Current(match.getQty());
		setA_CapvsExp(match.getC_InvoiceLine().getA_CapvsExp());
		setAssetAmtEntered(match.getC_InvoiceLine().getLineNetAmt());
		setC_Currency_ID(match.getC_InvoiceLine().getC_Invoice().getC_Currency_ID());
		setC_ConversionType_ID(match.getC_InvoiceLine().getC_Invoice().getC_ConversionType_ID());
		setDateDoc(match.getM_InOutLine().getM_InOut().getMovementDate());
		setDateAcct(match.getM_InOutLine().getM_InOut().getMovementDate());
		setC_DocType_ID();
	}

	/**
	 * Construct addition from invoice line
	 * @param invLine Invoice Line
	 */
	public MAssetAddition (MInvoiceLine invLine)
	{
		this(invLine.getCtx(), 0, invLine.get_TrxName());
		setAD_Org_ID(invLine.getAD_Org_ID());
		setC_Invoice_ID(invLine.getC_Invoice_ID());
		setC_InvoiceLine_ID(invLine.getC_InvoiceLine_ID());
		setC_DocType_ID();
		setA_CapvsExp(invLine.getA_CapvsExp());
		setPostingType(MAssetAddition.POSTINGTYPE_Actual);
		setA_SourceType(MAssetAddition.A_SOURCETYPE_Invoice);
		setDocStatus(DocAction.STATUS_Drafted);
		setDocAction(DocAction.ACTION_Complete);
		setA_CreateAsset(false);
		setIsAdjustUseLife(false);
		setIsAdjustAccmDepr(false);
		setA_Asset_ID(invLine.getA_Asset_ID());
		setC_Currency_ID(invLine.getC_Invoice().getC_Currency_ID());
		setC_ConversionType_ID(invLine.getC_Invoice().getC_ConversionType_ID());
		setDateAcct(invLine.getC_Invoice().getDateAcct());
		setDateDoc(invLine.getC_Invoice().getDateInvoiced());
		setAssetAmtEntered(invLine.getLineNetAmt());
		setDeltaUseLifeYears(0);

	}

	/**
	 * @author @win
	 * Construct addition from Project
	 * @param project 
	 */
	private MAssetAddition (MProject project)
	{
		this(project.getCtx(), 0, project.get_TrxName());
		if (log.isLoggable(Level.FINEST)) 
			log.finest("Entering: Project=" + project);

		setAD_Org_ID(project.getAD_Org_ID());
		setPostingType(POSTINGTYPE_Actual);
		setA_SourceType(A_SOURCETYPE_Project);
		//
		setC_Currency_ID(project.getC_Currency_ID());
		if (project.get_ValueAsInt("C_ConversionType_ID")>0) {
			setC_ConversionType_ID(project.get_ValueAsInt("C_ConversionType_ID"));
		}
		setAssetAmtEntered(project.getProjectBalanceAmt());
		setDeltaUseLifeYears(I_ZERO);
		setDeltaUseLifeYears_F(I_ZERO);
		setC_DocType_ID();
		setC_Project_ID(project.get_ID());
	}

	/**
	 * Copy fields from MatchInv+InvoiceLine+InOutLine
	 * @param model - to copy from
	 * @param M_MatchInv_ID - matching invoice id
	 * @param newRecord new object model is created
	 */
	public static boolean setM_MatchInv(SetGetModel model, int M_MatchInv_ID)
	{
		boolean newRecord = false;
		String trxName = null;
		if (model instanceof PO)
		{
			PO po = (PO)model;
			newRecord = po.is_new();
			trxName = po.get_TrxName();

		}

		if (s_log.isLoggable(Level.FINE)) s_log.fine("Entering: model=" + model + ", M_MatchInv_ID=" + M_MatchInv_ID + ", newRecord=" + newRecord + ", trxName=" + trxName);

		final String qMatchInv_select = "SELECT"
				+ "  C_Invoice_ID"
				+ ", C_InvoiceLine_ID"
				+ ", M_InOutLine_ID"
				+ ", M_Product_ID"
				+ ", M_AttributeSetInstance_ID"
				+ ", Qty AS "+COLUMNNAME_A_QTY_Current
				+ ", InvoiceLine AS "+COLUMNNAME_Line
				+ ", M_Locator_ID"
				+ ", A_CapVsExp"
				+ ", MatchNetAmt AS "+COLUMNNAME_AssetAmtEntered
				+ ", MatchNetAmt AS "+COLUMNNAME_AssetSourceAmt
				+ ", C_Currency_ID"
				+ ", C_ConversionType_ID"
				+ ", MovementDate AS "+COLUMNNAME_DateDoc
				;
		final String qMatchInv_from = " FROM mb_matchinv WHERE M_MatchInv_ID="; //@win change M_MatchInv_ARH to M_MatchInv

		String query = qMatchInv_select;
		if (newRecord) {
			query += ", A_Asset_ID, A_CreateAsset";
		}
		query += qMatchInv_from + M_MatchInv_ID;

		/*
		PreparedStatement pstmt = null;
		ResultSet rs = null;

		try
		{
			pstmt = DB.prepareStatement(query, trxName);
			DB.setParameters(pstmt, params);
			rs = pstmt.executeQuery();
			updateColumns(models, columnNames, rs);
		}
		catch (SQLException e)
		{
			throw new DBException(e, query);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null; pstmt = null;
		}
		 */
		SetGetUtil.updateColumns(model, null, query, trxName);

		s_log.fine("Leaving: RETURN TRUE");
		return true;
	}

	/**
	 *
	 */
	public void setIsApproved()
	{
		if(!isProcessed())
		{
			String str = Env.getContext(getCtx(), "#IsCanApproveOwnDoc");
			boolean isApproved = "Y".equals(str);
			if (log.isLoggable(Level.FINE)) log.fine("#IsCanApproveOwnDoc=" + str + "=" + isApproved);
			setIsApproved(isApproved);
		}
	}


	public Timestamp getDateAcct()
	{
		Timestamp dateAcct = super.getDateAcct();
		if (dateAcct == null) {
			dateAcct = getDateDoc();
			setDateAcct(dateAcct);
		}
		return dateAcct;
	}


	public boolean processIt (String processAction)
	{
		m_processMsg = null;
		DocumentEngine engine = new DocumentEngine (this, getDocStatus());
		return engine.processIt (processAction, getDocAction());
	}	//	processIt

	/**	Process Message 			*/
	private String		m_processMsg = null;
	/**	Just Prepared Flag			*/
	private boolean		m_justPrepared = false;


	public boolean unlockIt()
	{
		if (log.isLoggable(Level.INFO)) log.info("unlockIt - " + toString());
		//	setProcessing(false);
		return true;
	}	//	unlockIt


	public boolean invalidateIt()
	{
		if (log.isLoggable(Level.INFO)) log.info("invalidateIt - " + toString());
		return false;
	}	//	invalidateIt


	public String prepareIt()
	{
		if (log.isLoggable(Level.INFO)) log.info(toString());

		// Call model validators
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_PREPARE);
		if (m_processMsg != null)
		{
			return DocAction.STATUS_Invalid;
		}

		MPeriod.testPeriodOpen(getCtx(), getDateAcct(), MDocType.DOCBASETYPE_FixedAssetsAddition, getAD_Org_ID());

		//@phie add new column isLowAssetGroup
		//isLowValueAsset allow assetValue 0
		MAssetGroup assetGroup = new MAssetGroup(getCtx(), getA_Asset_Group_ID(), get_TrxName());
		// Check AssetValueAmt != 0
		if (getAssetValueAmt().signum() == 0 && !assetGroup.get_ValueAsBoolean("isLowValueAsset")) {
			m_processMsg="@Invalid@ @AssetValueAmt@=0";
			return DocAction.STATUS_Invalid;
		}



		// If new assets (not renewals) must have nonzero values
		if (getAssetValueAmt().signum() <= 0 && !assetGroup.get_ValueAsBoolean("isLowValueAsset"))
		{
			throw new AssetException("Asset value amt must be greater than zero");
		}
		
		// Validate Source - Project
		//TODO: @win note: this code restrict one project can only convert to one asset.
		//It is acceptable for current situation but must be adjusted once we want to allow create multi asset from one project
		if (A_SOURCETYPE_Project.equals(getA_SourceType()))
		{
			if (getC_Project_ID() <= 0)
				throw new FillMandatoryException(COLUMNNAME_C_Project_ID);

			final String whereClause = COLUMNNAME_C_Project_ID+"=?"
					+" AND DocStatus IN ('IP','CO','CL')"
					+" AND "+COLUMNNAME_A_Asset_Addition_ID+"<>?";

			List<MAssetAddition> list = new Query(getCtx(), Table_Name, whereClause, get_TrxName())
			.setParameters(new Object[]{getC_Project_ID(), get_ID()})
			.list();

			if (list.size() > 0)
			{
				StringBuilder sb = new StringBuilder("You can not create project for this asset,"
						+" Project already has assets. View: ");
				for (MAssetAddition aa : list)
					sb.append(aa.getDocumentInfo()).append("; ");

				throw new AssetException(sb.toString());
			}
		}

		// Call model validators
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_PREPARE);
		if (m_processMsg != null)
		{
			return DocAction.STATUS_Invalid;
		}

		//	Done
		m_justPrepared = true;

		if (!DOCACTION_Complete.equals(getDocAction()))
			setDocAction(DOCACTION_Complete);

		return DocAction.STATUS_InProgress;
	}	//	prepareIt


	public boolean approveIt()
	{
		if (log.isLoggable(Level.INFO)) log.info("approveIt - " + toString());
		setIsApproved(true);
		return true;
	}	//	approveIt


	public boolean rejectIt()
	{
		if (log.isLoggable(Level.INFO)) log.info("rejectIt - " + toString());
		setIsApproved(false);
		return true;
	}	//	rejectIt


	public String completeIt() 
	{
		//	Re-Check
		if (!m_justPrepared)
		{
			String status = prepareIt();
			if (!DocAction.STATUS_InProgress.equals(status))
				return status;
		}

		//	User Validation
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_COMPLETE);
		if (m_processMsg != null) {
			return DocAction.STATUS_Invalid;
		}

		//	Implicit Approval
		if (!isApproved())
			approveIt();

		if (log.isLoggable(Level.INFO)) log.info(toString());

		// Check/Create ASI:
		checkCreateASI();

		String additionType = ADDITION_TYPE_ADDITION;

		if (!isA_CreateAsset()) {
			if (getA_CapvsExp().equals(MAssetAddition.A_CAPVSEXP_Capital))
				additionType = ADDITION_TYPE_ADJUSTMENT;
			else 
				additionType = ADDITION_TYPE_EXPENDITURE;
		}

		MAsset asset = null;
		
		if (additionType==ADDITION_TYPE_ADDITION) {
			switch (getA_SourceType()) {
			case (MAssetAddition.A_SOURCETYPE_Project): 
				asset = new MAsset((MProject) getC_Project());
			break;
			case (MAssetAddition.A_SOURCETYPE_Invoice) :
				asset = new MAsset((MMatchInv) getM_MatchInv());
			break;
			default :
				asset = new MAsset(getCtx(), 0, get_TrxName());
				asset.setClientOrg(Env.getAD_Client_ID(getCtx()), getAD_Org_ID());
			}
			asset.setValue(getA_NewAsset_Value());
			asset.setName(getA_NewAsset_Name());
			asset.setA_Asset_Group_ID(getA_Asset_Group_ID());
			asset.setA_Asset_Status(MAsset.A_ASSET_STATUS_New);
			asset.saveEx();

			setA_Asset(asset);
		} else {
			asset = getA_Asset(true);
		}
		
		/* commented by @Stephan
		if(!asset.isDepreciated())
		{
			m_processMsg = "@AssetIsNotDepreciating@";
			return DocAction.STATUS_Invalid;
		}*/
		
		// Only New assets can be activated
		if (isA_CreateAsset() && !MAsset.A_ASSET_STATUS_New.equals(asset.getA_Asset_Status()))
		{
			throw new AssetException("Only new assets can be activated");
		}

		// Get/Create Asset Workfile
		MDepreciationWorkfile assetwk = MDepreciationWorkfile.get(getCtx(), getA_Asset_ID(), getPostingType(), get_TrxName());
		if (log.isLoggable(Level.FINE)) log.fine("workfile: " + assetwk);

		if (assetwk == null && asset != null)
			assetwk = new MDepreciationWorkfile(asset, getPostingType(), null);

		if (additionType!=ADDITION_TYPE_EXPENDITURE) {
			//loading asset
			asset = getA_Asset(!m_justPrepared); // requery if not just prepared
			if (log.isLoggable(Level.FINE)) log.fine("asset=" + asset);

			//Cannot proceed if addition date is before last depreciation processing date
			if (additionType==ADDITION_TYPE_ADJUSTMENT) {
				if (assetwk.isDepreciated(getDateAcct()))
					throw new AssetAlreadyDepreciatedException();
			}
			
			MDepreciationExp.checkExistsNotProcessedEntries(assetwk.getCtx(), assetwk.getA_Asset_ID(), 
					getDateAcct(), assetwk.getPostingType(), assetwk.get_TrxName());

			assetwk.adjustCost(getAssetValueAmt(), getA_QTY_Current(), isA_CreateAsset()); // reset if isA_CreateAsset
			assetwk.setDateAcct(getDateAcct());
			assetwk.setProcessed(true);

			// Creating/Updating asset product
			updateA_Asset_Product(false);

			// Changing asset status to Activated or Depreciated
			if (additionType==ADDITION_TYPE_ADDITION) {
				//@phie 2744
				asset.setAssetServiceDate(getDateAcct());
				asset.set_ValueOfColumn("AcquisitionDate", getDateDoc());
				//end phie
				
				if (isAdjustAccmDepr()) {
					assetwk.setA_Current_Period(getA_Period_Start());
					assetwk.setA_Accumulated_Depr(getA_Accumulated_Depr());
					assetwk.setA_Accumulated_Depr_F(getA_Accumulated_Depr_F());
				}
				else {
					assetwk.setA_Current_Period(1);
				}

				if (this.getA_Salvage_Value().signum() > 0)
					assetwk.setA_Salvage_Value(this.getA_Salvage_Value());

				if (isA_CreateAsset()) {
						assetwk.setA_Accumulated_Depr(getA_Accumulated_Depr());
				}
				assetwk.adjustUseLife(getUseLifeYears(), getUseLifeYears(), isA_CreateAsset());
				asset.changeStatus(MAsset.A_ASSET_STATUS_Activated, getDateAcct());
				assetwk.setA_Period_Start(getA_Period_Start());
				assetwk.setA_Current_Period(getA_Period_Start());
			} else {
				assetwk.adjustUseLife(getDeltaUseLifeYears(), getDeltaUseLifeYears_F(), isA_CreateAsset());
			}

			//must save here as assetwk is reloaded on buildDepreciation
			assetwk.saveEx();
			asset.saveEx();
			Trx.get(asset.get_TrxName(), true).commit();
			
			// Rebuild depreciation:
			assetwk.buildDepreciation();
		}

		//
		updateSourceDocument(false);
		MAssetChange.createAddition(this, assetwk);

		// finish
		setProcessed(true);
		setDocAction(DOCACTION_Close);
		//
		//	User Validation
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_COMPLETE);
		if (m_processMsg != null) {
			return DocAction.STATUS_Invalid;
		}
		//
		return DocAction.STATUS_Completed;
	}	//	completeIt


	public boolean voidIt()
	{
		// Before Void
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_VOID);
		if (m_processMsg != null)
			return false;

		if (getDocStatus().equals(DocAction.STATUS_Completed) || getDocStatus().equals(DocAction.STATUS_Closed)) {
			reverseIt(false);			
		} else {
			setDocStatus(DocAction.STATUS_Voided);
		}

		//	User Validation
		String errmsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_VOID);
		if (errmsg != null) {
			m_processMsg = errmsg;
			return false;
		}

		// finish
		setProcessed(true);
		setDocAction(DOCACTION_None);
		return true;
	}	//	voidIt

	private void reverseIt(boolean isReActivate)
	{
		if (DOCSTATUS_Closed.equals(getDocStatus())
				|| DOCSTATUS_Reversed.equals(getDocStatus())
				|| DOCSTATUS_Voided.equals(getDocStatus())) {
			setDocAction(DOCACTION_None);
			throw new AssetException("Document Closed: " + getDocStatus());
		}

		//TODO: @win currently only support reversing expense asset addition
		if (!getA_CapvsExp().equals(MAssetAddition.A_CAPVSEXP_Expense))
			throw new AssetException("Only support reversal for expense adjustment for the moment");

		/*//@win temporary comment code related to capital asset addition
		// Handling Workfile
		MDepreciationWorkfile assetwk = MDepreciationWorkfile.get(getCtx(), getA_Asset_ID(), getPostingType(), get_TrxName());
		if (assetwk == null)
			throw new AssetException("@NotFound@ @A_DepreciationWorkfile_ID");

		// TODO: Check if there are Additions after this one

		if (assetwk.isFullyDepreciated())
		{
			throw new AssetNotImplementedException("Unable to verify if it is fully depreciated");
		}

		// cannot update a previous period
		if (!isA_CreateAsset() && assetwk.isDepreciated(getDateAcct()))
		{
			throw new AssetAlreadyDepreciatedException();
		}


		// adjust the asset value
		assetwk.adjustCost(getAssetValueAmt().negate(), getA_QTY_Current().negate(), false);
		assetwk.adjustUseLife(0 - getDeltaUseLifeYears(), 0 - getDeltaUseLifeYears_F(), false);
		assetwk.saveEx();
		 */

		//
		// Delete Expense Entries that were created by this addition
		{
			final String whereClause = MDepreciationExp.COLUMNNAME_A_Asset_Addition_ID+"=?"
					+" AND "+MDepreciationExp.COLUMNNAME_PostingType+"=?";
			List<MDepreciationExp>
			list = new Query(getCtx(), MDepreciationExp.Table_Name, whereClause, get_TrxName())
			.setParameters(new Object[]{get_ID(), MDepreciationExp.POSTINGTYPE_Actual})
			.setOrderBy(MDepreciationExp.COLUMNNAME_DateAcct+" DESC, "+MDepreciationExp.COLUMNNAME_A_Depreciation_Exp_ID+" DESC")
			.list();
			for (MDepreciationExp depexp: list) {
				depexp.deleteEx(true);
			}
		}
		//

		/*//@win temporary comment code related to capital asset addition
		// Update/Delete working file (after all entries were deleted)
		if (isA_CreateAsset())
		{
			assetwk.deleteEx(true);
		}
		else
		{
			assetwk.setA_Current_Period();
			assetwk.saveEx();
			assetwk.buildDepreciation();
		}

		// Creating/Updating asset product
		updateA_Asset_Product(true);

		// Change Asset Status
		if (isA_CreateAsset())
		{
			MAsset asset = getA_Asset(true);
			asset.changeStatus(MAsset.A_ASSET_STATUS_New, getDateAcct());
			//asset.isDepreciated();
			//asset.setIsDepreciated(true);
			asset.saveEx();


			if (!isReActivate)
			{
				setA_CreateAsset(false); // reset flag
			}
		}
		 */

		MFactAcct.deleteEx(get_Table_ID(), get_ID(), get_TrxName());
		updateSourceDocument(true);
	}


	public boolean closeIt()
	{
		if (log.isLoggable(Level.INFO)) log.info("closeIt - " + toString());
		setDocAction(DOCACTION_None);
		return true;
	}	//	closeIt


	public boolean reverseCorrectIt()
	{
		throw new AssetNotImplementedException("reverseCorrectIt");
	}	//	reverseCorrectionIt


	public boolean reverseAccrualIt()
	{
		throw new AssetNotImplementedException("reverseAccrualIt");
	}	//	reverseAccrualIt


	public boolean reActivateIt()
	{
		throw new AssetNotImplementedException("reActivateIt");
	}	//	reActivateIt


	public String getSummary()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("@DocumentNo@ #").append(getDocumentNo())
		.append(": @A_CreateAsset@=@").append(isA_CreateAsset() ? "Y" : "N").append("@");

		MAsset asset = getA_Asset(false);
		if (asset != null) {
			sb.append(", @A_Asset_ID@=").append(asset.getName());
		}

		return Msg.parseTranslation(getCtx(), sb.toString());
	}	//	getSummary


	public String getProcessMsg()
	{
		return m_processMsg;
	}	//	getProcessMsg


	public int getDoc_User_ID()
	{
		return getCreatedBy();
	}	//	getDoc_User_ID


	public BigDecimal getApprovalAmt()
	{
		return getAssetValueAmt();
	}	//	getApprovalAmt


	/** Asset Cache */
	private final POCacheLocal<MAsset> m_cacheAsset = POCacheLocal.newInstance(this, MAsset.class);

	/**
	 * Get Asset 
	 * @param requery
	 * @return asset
	 */
	public MAsset getA_Asset(boolean requery)
	{
		return m_cacheAsset.get(requery);
	}

	/**
	 * Set Asset 
	 * @return asset
	 */
	private void setA_Asset(MAsset asset)
	{
		setA_Asset_ID(asset.getA_Asset_ID());
		m_cacheAsset.set(asset);
	} // setAsset

	/**
	 * Update Source Document (Invoice, Project etc) Status
	 * @param isReversal is called from a reversal action (like Void, Reverse-Correct).
	 * 					We need this flag because that when we call the method from voidIt()
	 * 					the document is not marked as voided yet. Same thing applies for reverseCorrectIt too. 
	 */
	private void updateSourceDocument(final boolean isReversalParam)
	{
		boolean isReversal = isReversalParam;

		// Check if this document is reversed/voided
		String docStatus = getDocStatus();
		if (!isReversal && (DOCSTATUS_Reversed.equals(docStatus) || DOCSTATUS_Voided.equals(docStatus)))
			isReversal = true;

		final String sourceType = getA_SourceType();

		// Invoice: mark C_InvoiceLine.A_Processed='Y' and set C_InvoiceLine.A_Asset_ID
		if (A_SOURCETYPE_Invoice.equals(sourceType) && isProcessed())
		{
			int C_InvoiceLine_ID = getC_InvoiceLine_ID();
			MInvoiceLine invoiceLine = new MInvoiceLine(getCtx(), C_InvoiceLine_ID, get_TrxName());
			invoiceLine.setA_Processed(!isReversal);
			invoiceLine.setA_Asset_ID(isReversal ? 0 : getA_Asset_ID());
			invoiceLine.saveEx();
		}

		// Project
		else if (A_SOURCETYPE_Project.equals(sourceType) && isProcessed())
		{
			if (isReversal)
			{
				MProject project = new MProject(getCtx(), getC_Project_ID(), get_TrxName());
				project.setIsCapitalized(false);
				project.saveEx();
			}
			else
			{
				//@win.. close the project before addition
				/*
				ProcessInfo pi = new ProcessInfo("", 0, MProject.Table_ID, getC_Project_ID());
				pi.setAD_Client_ID(getAD_Client_ID());
				pi.setAD_User_ID(Env.getAD_User_ID(getCtx()));

				ProjectClose proc = new ProjectClose();
				proc.startProcess(getCtx(), pi, Trx.get(get_TrxName(), false));
				if (pi.isError())
					throw new AssetException(pi.getSummary());
				 */
			}
		}
		// Manual
		else if (A_SOURCETYPE_Manual.equals(sourceType) && isProcessed())
		{
			// nothing to do
			log.fine("Nothing to do");
		}
	}

	/**
	 * Check/Create ASI for Product (if any). If there is no product, no ASI will be created
	 */
	private void checkCreateASI() 
	{
		MProduct product = MProduct.get(getCtx(), getM_Product_ID());
		// Check/Create ASI:
		MAttributeSetInstance asi = null;
		if (product != null && getM_AttributeSetInstance_ID() == 0)
		{
			asi = new MAttributeSetInstance(getCtx(), 0, get_TrxName());
			asi.setAD_Org_ID(product.getAD_Org_ID()); //@win, change from 0 
			asi.setM_AttributeSet_ID(product.getM_AttributeSet_ID());
			asi.saveEx();
			setM_AttributeSetInstance_ID(asi.getM_AttributeSetInstance_ID());
		}
	}

	/**
	 * Creating/Updating asset product
	 * @param isReversal
	 */
	private void updateA_Asset_Product(boolean isReversal)
	{
		// Skip if no product
		if (getM_Product_ID() <= 0){
			return;
		}

		MAssetProduct assetProduct = MAssetProduct.getCreate(getCtx(),
				getA_Asset_ID(), getM_Product_ID(), getM_AttributeSetInstance_ID(),
				get_TrxName());
		//
		if (assetProduct.get_ID() <= 0 && isReversal) {
			log.warning("No Product found "+this+" [IGNORE]");
			return;
		}
		//
		//TODO: Dead code
		BigDecimal adjQty = getA_QTY_Current();

		if (isReversal) {
			adjQty = adjQty.negate();
		}
		//
		assetProduct.addA_Qty_Current(getA_QTY_Current());
		assetProduct.setAD_Org_ID(getA_Asset().getAD_Org_ID()); 
		assetProduct.saveEx();

		if (isA_CreateAsset()) {
			MAsset asset = getA_Asset(false);
			assetProduct.updateAsset(asset);
			asset.saveEx();
		}
	}

	public File createPDF ()
	{
		return null;
	}	//	createPDF


	public String getDocumentInfo()
	{
		return getDocumentNo() + " / " + getDateDoc();
	}	//	getDocumentInfo


	public String toString()
	{
		StringBuilder sb = new StringBuilder("@DocumentNo@: " + getDocumentNo());
		MAsset asset = getA_Asset(false);
		if(asset != null && asset.get_ID() > 0)
		{
			sb.append(", @A_Asset_ID@: ").append(asset.getName());
		}
		return sb.toString();
	}	// toString

	private void setC_DocType_ID() 
	{
		StringBuilder sql = new StringBuilder ("SELECT C_DocType_ID FROM C_DocType ")
		.append( "WHERE AD_Client_ID=? AND AD_Org_ID IN (0,").append( getAD_Org_ID())
		.append( ") AND DocBaseType='FAA' ")
		.append( "ORDER BY AD_Org_ID DESC, IsDefault DESC");
		int C_DocType_ID = DB.getSQLValue(null, sql.toString(), getAD_Client_ID());
		if (C_DocType_ID <= 0)
			log.severe ("No FAA found for AD_Client_ID=" + getAD_Client_ID ());
		else
		{
			if (log.isLoggable(Level.FINE)) log.fine("(PO) - " + C_DocType_ID);
			setC_DocType_ID (C_DocType_ID);
		}

	}

	@Override
	public int customizeValidActions(String docStatus, Object processing,
			String orderType, String isSOTrx, int AD_Table_ID,
			String[] docAction, String[] options, int index) {
		for (int i = 0; i < options.length; i++) {
			options[i] = null;
		}

		index = 0;

		if (docStatus.equals(DocAction.STATUS_Drafted)) {
			options[index++] = DocAction.ACTION_Complete;
			options[index++] = DocAction.ACTION_Void;
		} else if (docStatus.equals(DocAction.STATUS_InProgress)) {
			options[index++] = DocAction.ACTION_Complete;
			options[index++] = DocAction.ACTION_Void;
		} else if (docStatus.equals(DocAction.STATUS_Invalid)) {
			options[index++] = DocAction.ACTION_Complete;
			options[index++] = DocAction.ACTION_Void;
		}

		return index;

	}	
}	//	MAssetAddition
