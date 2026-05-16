package com.mt.organizemessages

import android.content.Context
import android.content.Intent
import io.mockk.mockk
import org.junit.Test
import org.junit.Assert.assertEquals

class ReceiversAndServicesTest {

    private val context: Context = mockk(relaxed = true)

    @Test
    fun `SmsReceiver should not crash onReceive`() {
        val receiver = SmsReceiver()
        receiver.onReceive(context, Intent("android.provider.Telephony.SMS_DELIVER"))
    }

    @Test
    fun `MmsReceiver should not crash onReceive`() {
        val receiver = MmsReceiver()
        receiver.onReceive(context, Intent("android.provider.Telephony.WAP_PUSH_DELIVER"))
    }

    @Test
    fun `HeadlessSmsSendService should return null onBind and correct start command`() {
        val service = HeadlessSmsSendService()
        assertEquals(null, service.onBind(Intent()))
        assertEquals(2, service.onStartCommand(Intent(), 0, 0)) // START_NOT_STICKY = 2
    }
}
