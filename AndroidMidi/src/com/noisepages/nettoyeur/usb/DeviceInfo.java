/*
 * Copyright (C) 2013 Peter Brinkmann (peter.brinkmann@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.noisepages.nettoyeur.usb;

import java.util.Scanner;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.hardware.usb.UsbDevice;
import android.os.AsyncTask;

/**
 * Support for retrieving human-readable names of USB devices from the web (http://usb-ids.gowdy.us/index.html).
 * 
 * @author Peter Brinkmann (peter.brinkmann@gmail.com)
 */
public class DeviceInfo {

	private final String vendor;
	private final String product;

	/**
	 * Callback for asynchronous retrieval of device information.
	 */
	public static interface DeviceInfoCallback {

		/**
		 * Callback invoked on conclusion of retrieval of device information.
		 * 
		 * @param device for which to retrieve information
		 * @param info for the given device
		 */
		public void onDeviceInfo(UsbDevice device, DeviceInfo info);

		/**
		 * Callback invoked when no device info was found.
		 */
		public void onFailure(UsbDevice device);
	}

	/**
	 * Convenience method for asynchronous retrieval of device information.
	 * 
	 * Requires android.permission.INTERNET.
	 * 
	 * @param device for which to retrieve information
	 * @param callback to invoke upon completion; will be invoked on the main thread.
	 */
	public static void retrieveDeviceInfoAsync(final UsbDevice device, final DeviceInfoCallback callback) {
		new AsyncTask<UsbDevice, Void, DeviceInfo>() {
			@Override
			protected DeviceInfo doInBackground(UsbDevice... params) {
				return retrieveDeviceInfo(device);
			}

			@Override
			protected void onPostExecute(DeviceInfo result) {
				if (result != null) {
					callback.onDeviceInfo(device, result);
				} else {
					callback.onFailure(device);
				}
			};
		}.execute();
	}

	/**
	 * Synchronously retrieves device info from the web. This method must not be invoked on the main thread as it
	 * performs blocking network operations that may cause the app to become unresponsive.
	 * 
	 * Requires android.permission.INTERNET.
	 * 
	 * @param device for which to retrieve information
	 * @return device info, or null on failure
	 */
	public static DeviceInfo retrieveDeviceInfo(UsbDevice device) {
		return retrieveDeviceInfo(device.getVendorId(), device.getProductId());
	}

	private static String asFourDigitHex(int id) {
		return Integer.toHexString(0x10000 | id).substring(1);
	}

	/**
	 * Constructor for default instances of DeviceInfo, populated with numerical IDs rather than human-readable names.
	 */
	public DeviceInfo(UsbDevice device) {
		this(asFourDigitHex(device.getVendorId()), asFourDigitHex(device.getProductId()));
	}

	private DeviceInfo(String vendor, String product) {
		this.vendor = vendor;
		this.product = product;
	}

	@Override
	public String toString() {
		return vendor + ":" + product;
	}

	public String getVendor() {
		return vendor;
	}

	public String getProduct() {
		return product;
	}

	private static DeviceInfo retrieveDeviceInfo(int vendorId, int productId) {
		String vendorHex = asFourDigitHex(vendorId);
		String productHex = asFourDigitHex(productId);
		String url = "http://usb-ids.gowdy.us/read/UD/" + vendorHex;
		String vendorName = null;
		String productName = null;
		try {
			vendorName = getName(url);
			productName = getName(url + "/" + productHex);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (vendorName != null && productName != null) ? new DeviceInfo(vendorName, productName) : null;
	}

	private static String getName(String url) throws Exception {
		HttpClient client = new DefaultHttpClient();
		HttpGet request = new HttpGet(url);
		HttpResponse response = client.execute(request);
		Scanner scanner = new Scanner(response.getEntity().getContent());
		while (scanner.hasNext()) {
			String line = scanner.nextLine();
			int start = line.indexOf("Name:") + 6;
			if (start > 5) {
				int end = line.indexOf("<", start);
				return line.substring(start, end);
			}
		}
		return null;
	}
}