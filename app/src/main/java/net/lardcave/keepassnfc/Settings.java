/*
 * This is free and unencumbered software released into the public domain.
 * 
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 * 
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * 
 * For more information, please refer to [http://unlicense.org]
 */

package net.lardcave.keepassnfc;

class Settings {
	static final String nfc_mime_type = "application/x-keepassnfc-3";
	static final String nfcinfo_filename_template = "nfcinfo";

	// Only a single byte is used for the password length & it must be a multiple of 16
	static final int max_password_length = 15 * 16;

	// Stored on the tag:
	static final int KEY_TYPE_RAW = 1; // Password is stored directly on the card (basic NTAG203 memory-only tag)
	static final int KEY_TYPE_APP = 2; // Password is stored in the KeepassNFC applet (JavaCard smartcard).
	static final int key_type_length = 1;
	static final int key_length = 16; // AES

	// Stored elsewhere:
	// public static final int config_length = 1; // Config byte, currently set if ask for password
	
	static final int CONFIG_NOTHING = 0;
	static final int CONFIG_PASSWORD_ASK = 1;
}
