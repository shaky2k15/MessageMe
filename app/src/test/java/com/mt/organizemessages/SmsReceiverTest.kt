package com.mt.organizemessages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import com.mt.organizemessages.data.MessageRepository
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsReceiverTest {

    private val context: Context = mockk(relaxed = true)
    private val intent: Intent = mockk(relaxed = true)
    private val receiver = SmsReceiver()

    @Before
    fun setup() {
        mockkObject(SmsMessage::class)
        mockkConstructor(MessageRepository::class)
    }

    @Test
    fun `onReceive should handle null PDUs gracefully`() {
        every { intent.action } returns "android.provider.Telephony.SMS_RECEIVED"
        every { intent.extras } returns null
        
        receiver.onReceive(context, intent)
        // Should not crash
    }

    @Test
    fun `onReceive should handle empty PDUs`() {
        val bundle = Bundle()
        bundle.putSerializable("pdus", arrayOf<Any>())
        every { intent.action } returns "android.provider.Telephony.SMS_RECEIVED"
        every { intent.extras } returns bundle
        
        receiver.onReceive(context, intent)
        // Should not crash
    }
}
