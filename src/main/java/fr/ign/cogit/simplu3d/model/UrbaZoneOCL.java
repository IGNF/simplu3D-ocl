package fr.ign.cogit.simplu3d.model;

import java.util.ArrayList;
import java.util.List;

import fr.ign.cogit.simplu3d.model.UrbaZone;

public class UrbaZoneOCL extends UrbaZone {

	public UrbaZoneOCL(UrbaZone u) {
		super();
		this.setGeom(u.getGeom());
		this.setSubParcels(u.getSubParcels());

	}

	private List<Rule> rules = new ArrayList<Rule>();

	// Pour les règles de la zone urba
	public void setRules(List<Rule> rules) {
		this.rules = rules;
	}

	public List<Rule> getRules() {
		return rules;
	}

}
