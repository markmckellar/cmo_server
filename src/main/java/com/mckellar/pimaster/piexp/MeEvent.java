package com.mckellar.pimaster.piexp;

import java.util.HashMap;

public class MeEvent extends HashMap<Object,Object>{
	public String getMeName() {
		return(this.get("me_name").toString());
	}
	
	public boolean hasKey(String key) {
		return(this.containsKey(key));
	}
	public boolean hasMeTime() {
		return(this.hasKey("me_time"));
	}
	public Double getMeTime() {
		return(
				Double.parseDouble(
						this.get("me_time").toString()
						)
				);
	}
}
