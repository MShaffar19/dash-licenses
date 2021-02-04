/*************************************************************************
 * Copyright (c) 2019,2021 The Eclipse Foundation and others.
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which accompanies this
 * distribution, and is available at https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *************************************************************************/
package org.eclipse.dash.licenses;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.dash.licenses.context.IContext;
import org.eclipse.dash.licenses.spdx.SpdxExpressionParser;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

public class LicenseSupport {

	private Map<String, String> approvedLicenses;

	public enum Status {
		Approved, Restricted
	}

	private LicenseSupport(Map<String, String> approvedLicenses) {
		this.approvedLicenses = approvedLicenses;
	}

	public static LicenseSupport getLicenseSupport(IContext context) {
		Map<String, String> approvedLicenses = getApprovedLicenses(context.getSettings().getApprovedLicensesUrl());
		return new LicenseSupport(approvedLicenses);
	}

	public static LicenseSupport getLicenseSupport(Reader reader) {
		Map<String, String> approvedLicenses = getApprovedLicenses(reader);
		return new LicenseSupport(approvedLicenses);
	}

	private static Map<String, String> getApprovedLicenses(String url) {
		try {
			HttpClient client = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
			HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				return getApprovedLicenses(new StringReader(response.body()));
			}
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	private static Map<String, String> getApprovedLicenses(Reader contentReader) {
		JsonReader reader = Json.createReader(contentReader);
		JsonObject read = (JsonObject) reader.read();

		Map<String, String> licenses = new HashMap<>();
		JsonObject approved = read.getJsonObject("approved");
		if (approved != null) {
			approved.forEach((key, name) -> licenses.put(key.toUpperCase(), name.toString()));
		}

		// Augment the official list with licenses that are acceptable, but
		// not explicitly included in our approved list.
		licenses.put("EPL-1.0", "Eclipse Public License, v1.0");
		licenses.put("EPL-2.0", "Eclipse Public License, v2.0");
		licenses.put("WTFPL", "WTFPL");
		licenses.put("CC-BY-3.0", "CC-BY-3.0");
		licenses.put("CC-BY-4.0", "CC-BY-4.0");
		licenses.put("UNLICENSE", "Unlicense");
		licenses.put("ARTISTIC-2.0", "Artistic-2.0");
		licenses.put("BSD-2-Clause-FreeBSD", "BSD 2-Clause FreeBSD License");
		return licenses;
	}

	/**
	 * Answer the compatibility status of an SPDX expression with the approved
	 * licenses list. The expression could be the id of a single license (e.g.,
	 * "epl-1.0" or "apache-2.0"), or an expression (e.g., "epl-1.0 or apache-2.0").
	 * Matches are case insensitive.
	 * <p>
	 * The expression is considered "approved" if it is either a single license on
	 * the approved licenses list, or--in the case of a license expression--the
	 * expression matches entries in the approved licenses list in a valid
	 * configuration.
	 *
	 * @param expression an SPDX expression.
	 * @return <code>Status.Approved</code> when the expression is approve, or
	 *         <code>Status.Restricted</code> otherwise
	 */
	public Status getStatus(String expression) {
		if (expression == null || expression.trim().isEmpty())
			return Status.Restricted;

		if (new SpdxExpressionParser().parse(expression).matchesApproved(approvedLicenses.keySet())) {
			return Status.Approved;
		}

		return Status.Restricted;
	}
}
