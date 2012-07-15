package net.frontlinesms.plugins.payment.service.safaricomke;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.frontlinesms.FrontlineUtils;
import net.frontlinesms.data.domain.SmsMessage;
import net.frontlinesms.data.domain.PersistableSettings;
import net.frontlinesms.data.events.EntitySavedNotification;
import net.frontlinesms.data.repository.ContactDao;
import net.frontlinesms.events.FrontlineEventNotification;
import net.frontlinesms.plugins.payment.service.PaymentJob;
import net.frontlinesms.plugins.payment.service.PaymentJobProcessor;
import net.frontlinesms.plugins.payment.service.PaymentService;
import net.frontlinesms.plugins.payment.service.PaymentServiceException;
import net.frontlinesms.plugins.payment.service.PaymentStatus;

import org.apache.log4j.Logger;
import org.creditsms.plugins.paymentview.PaymentViewPluginController;
import org.creditsms.plugins.paymentview.analytics.TargetAnalytics;
import org.creditsms.plugins.paymentview.data.domain.Account;
import org.creditsms.plugins.paymentview.data.domain.IncomingPayment;
import org.creditsms.plugins.paymentview.data.repository.AccountDao;
import org.creditsms.plugins.paymentview.data.repository.ClientDao;
import org.creditsms.plugins.paymentview.data.repository.IncomingPaymentDao;
import org.creditsms.plugins.paymentview.data.repository.LogMessageDao;
import org.creditsms.plugins.paymentview.data.repository.OutgoingPaymentDao;
import org.creditsms.plugins.paymentview.data.repository.PaymentServiceSettingsDao;
import org.creditsms.plugins.paymentview.data.repository.TargetDao;

public abstract class AbstractPaymentService extends IntentReceiver implements PaymentService {
//> STATIC CONSTANTS
	/** Prefix attached to every property name. */
	private static final String PROPERTY_PREFIX = "plugins.payment.mpesa.";

	protected static final String PROPERTY_PIN = PROPERTY_PREFIX + "pin";
	protected static final String PROPERTY_BALANCE_CONFIRMATION_CODE = PROPERTY_PREFIX + "balance.confirmation";
	protected static final String PROPERTY_BALANCE_AMOUNT = PROPERTY_PREFIX + "balance.amount";
	protected static final String PROPERTY_BALANCE_DATE_TIME = PROPERTY_PREFIX + "balance.timestamp";
	protected static final String PROPERTY_BALANCE_UPDATE_METHOD = PROPERTY_PREFIX + "balance.update.method";
	protected static final String PROPERTY_SIM_IMSI = PROPERTY_PREFIX + "sim.imsi";
	protected static final String PROPERTY_OUTGOING_ENABLED = PROPERTY_PREFIX + "outgoing.enabled";
	protected static final String PROPERTY_BALANCE_ENABLED = PROPERTY_PREFIX + "balance.enabled";
	
//> INSTANCE PROPERTIES
	protected Logger log = FrontlineUtils.getLogger(this.getClass());
	protected TargetAnalytics targetAnalytics;
	protected PaymentJobProcessor outgoingJobProcessor;
	protected PaymentJobProcessor incomingJobProcessor;
	protected AccountDao accountDao;
	protected ClientDao clientDao;
	protected TargetDao targetDao;
	protected IncomingPaymentDao incomingPaymentDao;
	protected OutgoingPaymentDao outgoingPaymentDao;
	protected LogMessageDao logDao;
	protected PaymentServiceSettingsDao settingsDao;
	protected ContactDao contactDao;
	private PersistableSettings settings;
	private PaymentViewPluginController pluginController;

//> CONSTRUCTORS AND INITIALISERS
	public void init(PaymentViewPluginController pluginController) throws PaymentServiceException {
		this.pluginController = pluginController;
		this.accountDao = pluginController.getAccountDao();
		this.clientDao = pluginController.getClientDao();
		this.outgoingPaymentDao = pluginController.getOutgoingPaymentDao();
		this.targetDao = pluginController.getTargetDao();
		this.incomingPaymentDao = pluginController.getIncomingPaymentDao();
		this.targetAnalytics = pluginController.getTargetAnalytics();
		this.logDao = pluginController.getLogMessageDao();
		this.contactDao = pluginController.getUiGeneratorController().getFrontlineController().getContactDao();
		this.settingsDao = pluginController.getPaymentServiceSettingsDao();
				
		this.incomingJobProcessor = new PaymentJobProcessor(this);
		this.incomingJobProcessor.start();
		
		this.outgoingJobProcessor = new PaymentJobProcessor(this);
		this.outgoingJobProcessor.start();

		setCheckBalanceEnabled(smsModem.getCService().getAtHandler().supportsStk());
	}
	
	public void setLog(Logger log) {
		this.log = log;
	}
		
//> PERSISTENT PROPERTY ACCESSORS
	public String getBalanceConfirmationCode() {
		return getProperty(PROPERTY_BALANCE_CONFIRMATION_CODE, "");
	}
	public void setBalanceAmount(BigDecimal balanceAmount) {
		setProperty(PROPERTY_BALANCE_AMOUNT, balanceAmount);
	}
	public BigDecimal getBalanceAmount() {
		return getProperty(PROPERTY_BALANCE_AMOUNT, new BigDecimal("0"));
	}
	public void setBalanceConfirmationCode(String balanceConfirmationCode) {
		setProperty(PROPERTY_BALANCE_CONFIRMATION_CODE, balanceConfirmationCode);
	}
	public Date getBalanceDateTime() {
		return new Date(getProperty(PROPERTY_BALANCE_DATE_TIME, 0L));
	}
	public void setBalanceDateTime(Date balanceDateTime) {
		setProperty(PROPERTY_BALANCE_DATE_TIME, balanceDateTime.getTime());
	}
	public String getBalanceUpdateMethod() {
		return getProperty(PROPERTY_BALANCE_UPDATE_METHOD, "");
	}
	public void setBalanceUpdateMethod(String balanceUpdateMethod) {
		setProperty(PROPERTY_BALANCE_UPDATE_METHOD, balanceUpdateMethod);
	}
	public String getPin() {
		return getProperty(PROPERTY_PIN, PasswordString.class).getValue();
	}
	public void setPin(final String pin) {
		setProperty(PROPERTY_PIN, pin);
	}
	
	public boolean isOutgoingPaymentEnabled() {
		return getProperty(PROPERTY_OUTGOING_ENABLED, Boolean.class);
	}

	public void setOutgoingPaymentEnabled(boolean outgoingEnabled) {
		this.settings.set(PROPERTY_OUTGOING_ENABLED, outgoingEnabled);
	}
	
	public boolean isCheckBalanceEnabled() {
		return getProperty(PROPERTY_BALANCE_ENABLED, Boolean.class);
	}

	public void setCheckBalanceEnabled(boolean checkBalanceEnabled) {
		this.settings.set(PROPERTY_BALANCE_ENABLED, checkBalanceEnabled);
	}
		
	void updateBalance(BigDecimal amount, String confirmationCode, Date timestamp, String method) {
		setBalanceAmount(amount);
		setBalanceConfirmationCode(confirmationCode);
		setBalanceDateTime(timestamp);
		setBalanceUpdateMethod(method);
		settingsDao.updateServiceSettings(settings);
	}

//> CONFIGURABLE SERVICE METHODS
	public Class<? extends ConfigurableService> getSuperType() {
		return PaymentService.class;
	}
	
	public void startService() throws PaymentServiceException {
		queueOutgoingJob(new PaymentJob() {
			public void run() {
				//Run ish
			}
		});
	}

	public void stopService() {
		incomingJobProcessor.stop();
		outgoingJobProcessor.stop();
	}

//> EVENT OBSERVER METHODS
	@SuppressWarnings("rawtypes")
	public void notify(final FrontlineEventNotification notification) {
		if(notification instanceof EntitySavedNotification) {
			final Object entity = ((EntitySavedNotification) notification).getDatabaseEntity();
			if (entity instanceof SmsMessage) {
				final SmsMessage message = (SmsMessage) entity;
				processMessage(message);
			}
		}
	}
	
//> ABSTRACT SAFARICOM SERVICE METHODS
	protected abstract void processMessage(final SmsMessage message);
	abstract Date getTimePaid(SmsMessage message);
	abstract boolean isMessageTextValid(String message);
	abstract Account getAccount(SmsMessage message);
	abstract String getPaymentBy(SmsMessage message);
	protected abstract boolean isValidBalanceMessage(SmsMessage message);

//> UTILITY METHODS
	void queueIncomingJob(PaymentJob job) {
		incomingJobProcessor.queue(job);
	}
	void queueOutgoingJob(PaymentJob job) {
		outgoingJobProcessor.queue(job);
	}
	
	/** Gets a property from {@link #settings}.  This should be used for all non-user values. */
	<T extends Object> T getProperty(String key, T defaultValue) {
		return PersistableSettings.getPropertyValue(settings, key, defaultValue);
	}
	/** Gets a property from {@link #settings}. */
	<T extends Object> T getProperty(String key, Class<T> clazz) {
		return PersistableSettings.getPropertyValue(getPropertiesStructure(), settings, key, clazz);
	}
	/** Sets a property in {@link #settings}. */
	void setProperty(String key, Object value) {
		this.settings.set(key, value);
	}

	protected String getFirstMatch(final SmsMessage message, final String regexMatcher) {
		return getFirstMatch(message.getTextContent(), regexMatcher);
	}	
	protected String getFirstMatch(final String string, final String regexMatcher) {
		final Matcher matcher = Pattern.compile(regexMatcher).matcher(string);
		matcher.find();
		return matcher.group();
	}
	
	void updateStatus(PaymentStatus status) {
		//updateStatusBar(status.toString());
	}
	
	void reportPaymentFromNewClient(IncomingPayment payment){
		///(payment.getPaymentBy(), payment.getAmountPaid());
	}
}