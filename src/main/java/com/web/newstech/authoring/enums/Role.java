package com.web.newstech.authoring.enums;

public enum Role {

	ADMIN,
	AUTHOR;

	public String authority() {
		return "ROLE_" + name();
	}

}
