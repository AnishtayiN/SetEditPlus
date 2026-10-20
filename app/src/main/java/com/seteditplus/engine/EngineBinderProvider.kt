package com.seteditplus.engine

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.seteditplus.engine.server.BinderDelivery

/**
 * سرور داخلی Binder خودش را از اینجا به اپ تحویل می‌دهد.
 * فقط UID شل (2000) یا روت (0) پذیرفته می‌شود.
 */
class EngineBinderProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != BinderDelivery.METHOD_SEND_BINDER) return null
        val uid = Binder.getCallingUid()
        if (uid != 0 && uid != 2000) return null
        val binder = extras?.getBinder(BinderDelivery.EXTRA_BINDER) ?: return null
        EngineClient.onBinderReceived(binder)
        return Bundle()
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
