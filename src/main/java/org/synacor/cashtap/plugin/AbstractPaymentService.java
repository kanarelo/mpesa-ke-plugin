package org.synacor.cashtap.plugin.safaricom;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import org.synacor.cashtap.service.PaymentJob;
import org.synacor.cashtap.service.PaymentJobProcessor;
import org.synacor.cashtap.service.PaymentService;
import org.synacor.cashtap.service.PaymentServiceException;
import org.synacor.cashtap.service.PaymentStatus;

import org.synacor.cashtap.models.Account;
import org.synacor.cashtap.models.IncomingPayment;
import org.synacor.cashtap.models.SmsMessage;

import org.synacor.cashtap.models.daos.AccountDao;
import org.synacor.cashtap.models.daos.ContactDao;
import org.synacor.cashtap.models.daos.UserDao;
import org.synacor.cashtap.models.daos.IncomingPaymentDao;
import org.synacor.cashtap.models.daos.LogMessageDao;
import org.synacor.cashtap.models.daos.OutgoingPaymentDao;
import org.synacor.cashtap.models.daos.PaymentServiceSettingsDao;
import org.synacor.cashtap.models.daos.TargetDao;

public abstract class AbstractPaymentService extends IntentReceiver implements PaymentService {
//> STATIC CONSTANTS
	/** Prefix attached to every property name. */
//> INSTANCE PROPERTIES
	protected Logger log = Logger.getLogger(this.getClass());
	protected PaymentJobProcessor outgoingJobProcessor;
	protected PaymentJobProcessor incomingJobProcessor;
	protected AccountDao accountDao;
	protected UserDao clientDao;
	protected TargetDao targetDao;
	protected IncomingPaymentDao incomingPaymentDao;
	protected OutgoingPaymentDao outgoingPaymentDao;
	protected LogMessageDao logDao;
	protected PaymentServiceSettingsDao settingsDao;
	protected ContactDao contactDao;

//> CONSTRUCTORS AND INITIALISERS
	public void init() throws PaymentServiceException {
		/*
		this.accountDao = pluginController.getAccountDao();
		this.clientDao = pluginController.getUserDao();
		this.outgoingPaymentDao = pluginController.getOutgoingPaymentDao();
		this.targetDao = pluginController.getTargetDao();
		this.incomingPaymentDao = pluginController.getIncomingPaymentDao();
		this.targetAnalytics = pluginController.getTargetAnalytics();
		this.logDao = pluginController.getLogMessageDao();
		this.contactDao = pluginController.getUiGeneratorController().getFrontlineController().getContactDao();
		this.settingsDao = pluginController.getPaymentServiceSettingsDao();
		*/
		
		this.incomingJobProcessor = new PaymentJobProcessor(this);
		this.incomingJobProcessor.start();
		
		this.outgoingJobProcessor = new PaymentJobProcessor(this);
		this.outgoingJobProcessor.start();

		setCheckBalanceEnabled(smsModem.getCService().getAtHandler().supportsStk());
	}
		
	void updateBalance(BigDecimal amount, String confirmationCode, Date timestamp, String method) {
		setBalanceAmount(amount);
		setBalanceConfirmationCode(confirmationCode);
		setBalanceDateTime(timestamp);
		setBalanceUpdateMethod(method);
		settingsDao.updateServiceSettings(settings);
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
	/*@SuppressWarnings("rawtypes")
	public void notify(final FrontlineEventNotification notification) {
		if(notification instanceof EntitySavedNotification) {
			final Object entity = ((EntitySavedNotification) notification).getDatabaseEntity();
			if (entity instanceof SmsMessage) {
				final SmsMessage message = (SmsMessage) entity;
				processMessage(message);
			}
		}
	}*/
	
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
	
	void reportPaymentFromNewUser(IncomingPayment payment){
		///(payment.getPaymentBy(), payment.getAmountPaid());
	}
}