package org.synacor.cashtap.plugin.safaricom;

import java.util.List;

import net.frontlinesms.junit.BaseTestCase;
import org.synacor.cashtap.service.PaymentService;
import org.synacor.cashtap.service.PaymentServiceImplementationLoader;

public class ServiceLoaderTest extends BaseTestCase {
	public void testPersonalServiceLoading() {
		List<Class<? extends PaymentService>> services = new PaymentServiceImplementationLoader().getAll();
		assertTrue(services.contains(MpesaPersonalService.class));
	}
	
	public void testPayBillServiceLoading() {
		List<Class<? extends PaymentService>> services = new PaymentServiceImplementationLoader().getAll();
		assertTrue(services.contains(MpesaPayBillService.class));
	}
}
