package org.junoyoon.gitonline.model;


public class User {

	public User(String userId, String email) {
		this.userId = userId;
		this.email = email;
	}

	private String userId;

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	private String email;
}
