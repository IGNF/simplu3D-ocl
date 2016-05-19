package fr.ign.cogit.simplu3d.recalage3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.log4j.Logger;

import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.api.feature.IPopulation;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IDirectPosition;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IDirectPositionList;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.ILineString;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IPolygon;
import fr.ign.cogit.geoxygene.api.spatial.geomaggr.IMultiSurface;
import fr.ign.cogit.geoxygene.api.spatial.geomprim.IOrientableSurface;
import fr.ign.cogit.geoxygene.api.spatial.geomprim.IPoint;
import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
import fr.ign.cogit.geoxygene.contrib.cartetopo.Arc;
import fr.ign.cogit.geoxygene.contrib.cartetopo.CarteTopo;
import fr.ign.cogit.geoxygene.contrib.cartetopo.Face;
import fr.ign.cogit.geoxygene.contrib.cartetopo.Groupe;
import fr.ign.cogit.geoxygene.feature.DefaultFeature;
import fr.ign.cogit.geoxygene.feature.FT_FeatureCollection;
import fr.ign.cogit.geoxygene.sig3d.calculation.Util;
import fr.ign.cogit.geoxygene.sig3d.convert.geom.FromGeomToSurface;
import fr.ign.cogit.geoxygene.sig3d.convert.geom.FromPolygonToLineString;
import fr.ign.cogit.geoxygene.sig3d.convert.transform.Extrusion2DObject;
import fr.ign.cogit.geoxygene.sig3d.geometry.Box3D;
import fr.ign.cogit.geoxygene.spatial.coordgeom.GM_LineString;
import fr.ign.cogit.geoxygene.spatial.geomaggr.GM_MultiSurface;
import fr.ign.cogit.geoxygene.spatial.geomprim.GM_Point;
import fr.ign.cogit.geoxygene.util.attribute.AttributeManager;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileWriter;
import fr.ign.cogit.geoxygene.util.index.Tiling;
import fr.ign.cogit.simplu3d.checker.VeryFastRuleChecker;
import fr.ign.cogit.simplu3d.model.AbstractBuilding;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.cache.CacheModelInstance;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.impl.Cuboid;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.loader.LoaderCuboid2;
import fr.ign.cogit.simplu3d.util.PointInPolygon;

/**
 * Class that enables the fusion of Cuboid Geometry in order to make consistant
 * geomtries one by path of cuboid
 * 
 * This software is released under the licence CeCILL
 * 
 * see LICENSE.TXT
 * 
 * see <http://www.cecill.info/ http://www.cecill.info/
 * 
 * 
 * 
 * @copyright IGN
 * 
 * @author Brasebin Mickaël
 * 
 * @version 1.0
 **/
public class Recal3D {

	private static Logger logger = Logger.getLogger(Recal3D.class);

	// public static IFeatureCollection<IFeature> DEBUG = new
	// FT_FeatureCollection<>();

	/**
	 * @param args
	 * @throws CloneNotSupportedException
	 */
	public static void main(String[] args) throws CloneNotSupportedException {

		// Paramters : Currently the DTM is not managed so it is necessary to
		// set a Zmin
		double zMin = 139.;
		double topologicalMapThreshold = 0.5;
		double connexionDistance = 0.5;
		// Folder in
		String strShpOut = "/home/mickael/temp/";
		String shpeIn = strShpOut + "shp_9.0_ 0.25_0_ene-47524.50287299342.shp";

		// Load Cuboid from a generate ShapeFile (we can use date output from
		// the optimization algorithm)
		List<Cuboid> lCuboid = LoaderCuboid2.loadFromShapeFile(shpeIn);

		// Do not forget to set the 3D geometry
		for (Cuboid c : lCuboid) {
			c.setGeom(c.generated3DGeom());
		}

		// The output collection
		IFeatureCollection<IFeature> featColl = new FT_FeatureCollection<>();

		// Fusionne geom fonction allow the fusion
		featColl.addAll(fusionneGeom(lCuboid, zMin, connexionDistance, topologicalMapThreshold));
		
		featColl = seperateRoof(featColl);

		// Output shapefile
		ShapefileWriter.write(featColl, strShpOut + "test2.shp");

		// ShapefileWriter.write(DEBUG, strShpOut + "debug.shp");

	}
	
	public static IFeatureCollection<IFeature> seperateRoof( IFeatureCollection<IFeature>  featC){
		
		IFeatureCollection<IFeature> featCOut = new FT_FeatureCollection<>();
		
		int id = 0;
		
		for(IFeature feat : featC){
			
			id++;
			
				
			IMultiSurface<IOrientableSurface> ims = FromGeomToSurface.convertMSGeom(feat.getGeom());
			IMultiSurface<IOrientableSurface> nonV = Util.detectNonVertical(ims.getList(), 0.2);
			IMultiSurface<IOrientableSurface> v = Util.detectVertical(ims.getList(), 0.2);
			
			
			IFeature feat1 = new DefaultFeature(v);
			IFeature feat2 = new DefaultFeature(nonV);
			
			AttributeManager.addAttribute(feat1, "ID", id, "Integer"); 
			AttributeManager.addAttribute(feat2, "ID", id, "Integer"); 
			
			featCOut.add(feat1);
			featCOut.add(feat2);
		}
		
		
		
		
		return featCOut;
		
	}

	/**
	 * Fusionne geom is the main function of this script, it uses a set of
	 * Cuboid and the zMin in order to give an inferior value to extrusoin
	 * 
	 * @param lAB
	 * @param zMini
	 * @return
	 */
	private static IFeatureCollection<IFeature> fusionneGeom(List<Cuboid> lAB, double zMini, double connexionDistance,
			double topologicalMapThreshold) {

		IFeatureCollection<IFeature> featFus = new FT_FeatureCollection<>();

		// In order to speed up calculation the cuboid are separated into
		// connected sets of cuboid
		List<List<Cuboid>> lGroupe = createGroup(lAB, connexionDistance);

		int count = 0;
		for (List<Cuboid> groupe : lGroupe) {

			System.out.println("groupe " + (++count));
			// The fusion is processed on each group
			IFeatureCollection<IFeature> featCTemp = fusionneGeomByGroup(groupe, zMini, topologicalMapThreshold);

			if (featCTemp != null) {
				featFus.addAll(featCTemp);
			}

		}

		return featFus;

	}

	/**
	 * The create group function that separates cuboid into list of near cuboid
	 * 
	 * @param lBatIn
	 * @param connexionDistance
	 *            : minimal distance to consider 2 boxes as connected
	 * @return
	 */
	public static List<List<Cuboid>> createGroup(List<Cuboid> lBatIn, double connexionDistance) {

		List<List<Cuboid>> listGroup = new ArrayList<>();

		while (!lBatIn.isEmpty()) {

			Cuboid batIni = lBatIn.remove(0);

			List<Cuboid> currentGroup = new ArrayList<>();
			currentGroup.add(batIni);

			int nbElem = lBatIn.size();

			bouclei: for (int i = 0; i < nbElem; i++) {

				for (Cuboid batTemp : currentGroup) {

					if (lBatIn.get(i).getFootprint().distance(batTemp.getFootprint()) < connexionDistance) {

						currentGroup.add(lBatIn.get(i));
						lBatIn.remove(i);
						i = -1;
						nbElem--;
						continue bouclei;

					}
				}

			}

			listGroup.add(currentGroup);
		}

		/*
		 * int count = 0;
		 * 
		 * for(List<Cuboid> lC : listGroup){ count ++; for(Cuboid c:lC){
		 * IFeature feat = new DefaultFeature(c.generated3DGeom());
		 * AttributeManager.addAttribute(feat, "G", count, "Integer");
		 * DEBUG.add(feat);
		 * 
		 * } }
		 */

		return listGroup;

	}

	/**
	 * This class is used to cut the cuboid into a set of non-intersected
	 * polygons
	 * 
	 * @param lAB
	 * @param threshold
	 * @return
	 */
	public static List<IOrientableSurface> cutGeometry(List<? extends Cuboid> lAB, double threshold) {
		// We initialize the height and the footprints of this cuboid
		List<IOrientableSurface> lOS = new ArrayList<>();
		List<Double> lHeight = new ArrayList<>();

		for (Cuboid ab : lAB) {

			lOS.add(ab.getFootprint());
			lHeight.add(ab.height(0, 1));
		}

		boolean hasChange = true;
		// We make a boucle until there is no new cut
		booclewhile: while (hasChange) {
			hasChange = false;

			int nbElem = lOS.size();

			for (int i = 0; i < nbElem; i++) {

				IOrientableSurface osi = lOS.get(i);

				for (int j = i + 1; j < nbElem; j++) {

					IOrientableSurface osj = lOS.get(j);

					// If the geometries are not intersected we go on
					if (!osi.intersects(osj) || (osi.intersection(osj).area() < 0.2)) {
						continue;
					}

					// We get the height of the intersected geometries
					double heighti = lHeight.get(i);
					double heightj = lHeight.get(j);

					// On met à jour la liste
					if (i < j) {
						lHeight.remove(j);
						lHeight.remove(i);
						lOS.remove(j);
						lOS.remove(i);

					} else {
						lHeight.remove(i);
						lHeight.remove(j);
						lOS.remove(i);
						lOS.remove(j);
					}

					// Same height (with a threshold) we make an union and
					// affect the same height
					if (Math.abs(heightj - heighti) < threshold) {

						IGeometry geom = osi.union(osj);

						List<IOrientableSurface> lOSTemp = FromGeomToSurface.convertGeom(geom);
						for (IOrientableSurface osTemp : lOSTemp) {

							lOS.add(osTemp);
							lHeight.add(heighti);

						}

						if (!lOSTemp.isEmpty()) {
							hasChange = true;
							continue booclewhile;
						}
					}

					// The height is different we remove a part of the geometry
					// of the lowest polygon
					if (heighti < heightj) {

						IGeometry geom = osi.difference(osj);
						List<IOrientableSurface> lOSTemp = FromGeomToSurface.convertGeom(geom);

						for (IOrientableSurface osTemp : lOSTemp) {

							lOS.add(osTemp);
							lHeight.add(heighti);

							lOS.add(osj);
							lHeight.add(heightj);

						}

						if (!lOSTemp.isEmpty()) {
							hasChange = true;
							continue booclewhile;
						}

					}

					IGeometry geom = osj.difference(osi);
					List<IOrientableSurface> lOSTemp = FromGeomToSurface.convertGeom(geom);

					for (IOrientableSurface osTemp : lOSTemp) {

						lOS.add(osTemp);
						lHeight.add(heightj);

						lOS.add(osi);
						lHeight.add(heighti);

					}

					if (!lOSTemp.isEmpty()) {
						hasChange = true;
						continue booclewhile;
					}

				}

			}

		}
		/*
		 * for (IOrientableSurface os : lOS) { DEBUG.add(new
		 * DefaultFeature(os)); }
		 */

		return lOS;

	}

	/**
	 * This funciton allow to make the fusion by groups of geometries
	 * 
	 * @param lAB
	 * @param zMini
	 * @param threshold
	 *            : Topologic map threshold
	 * @return
	 */
	private static IFeatureCollection<IFeature> fusionneGeomByGroup(List<Cuboid> lAB, double zMini, double threshold) {

		String attrzmax = "Temp";

		List<IOrientableSurface> lOSIni = cutGeometry(lAB, threshold);

		// Preparation of topologic map faces
		IFeatureCollection<IFeature> featC = new FT_FeatureCollection<>();

		for (IOrientableSurface osTemp : lOSIni) {

			Face f = new Face();
			f.setGeometrie((IPolygon) osTemp.clone());

			featC.add(f);

		}

		// Creation of the topologic map
		CarteTopo carteTopo = newCarteTopo("-aex90", featC, threshold);

		IFeatureCollection<IFeature> featC2 = new FT_FeatureCollection<>();

		// The z is retrived for all faces of the topological map
		for (AbstractBuilding aB : lAB) {

			IFeature feature = new DefaultFeature((IPolygon) aB.getFootprint());

			Box3D b = new Box3D(aB.getGeom());

			System.out.println(zMini + b.getURDP().getZ());

			AttributeManager.addAttribute(feature, attrzmax, zMini + b.getURDP().getZ(), "Double");

			featC2.add(feature);
		}

		featC2.initSpatialIndex(Tiling.class, false);

		Groupe gr = carteTopo.getPopGroupes().nouvelElement();
		gr.setListeArcs(carteTopo.getListeArcs());
		gr.setListeFaces(carteTopo.getListeFaces());
		gr.setListeNoeuds(carteTopo.getListeNoeuds());

		List<Groupe> lG = gr.decomposeConnexes();

		IFeatureCollection<IFeature> featCollOut = new FT_FeatureCollection<>();

		logger.info("NB Groupes : " + lG.size());
		System.out.println("NB Groupes : " + lG.size());

		// For each group the faces are treated (normally there is only one)
		for (Groupe g : lG) {

			List<IOrientableSurface> lOS = new ArrayList<>();

			List<Face> lF = new ArrayList<>();
			// We prepare the list of available faces
			for (Arc a : g.getListeArcs()) {

				Face fg = a.getFaceDroite();
				Face fd = a.getFaceGauche();

				if (fg != null) {

					if (!lF.contains(fg)) {
						lF.add(fg);
					}

				}

				if (fd != null) {

					if (!lF.contains(fd)) {
						lF.add(fd);
					}

				}

			}

			// For each face we look at the maximal height
			for (Face f : lF) {

				if (f.isInfinite()) {
					continue;
				}

				IPoint p = new GM_Point(PointInPolygon.get(f.getGeometrie()));// f.getGeometrie().buffer(-0.05).coord().get(0));

				if (!f.getGeometrie().contains(p)) {
					logger.warn("Point not in polygon");
				}

				Collection<IFeature> featSelect = featC2.select(p);

				double zMax = Double.NEGATIVE_INFINITY;

				if (featSelect.isEmpty()) {

					zMax = zMini;

					logger.info("New empty face detected");
					// System.exit(666);
				}

				for (IFeature feat : featSelect) {
					zMax = Math.max(zMax, Double.parseDouble(feat.getAttribute(attrzmax).toString()));
				}

				IPolygon poly = (IPolygon) f.getGeometrie().clone();

				f.setArcsIgnores(zMax + "");

				// On affecte
				// AttributeManager.addAttribute(f, attrzmax, zMax, "Double");
				for (IDirectPosition dp : poly.coord()) {
					dp.setZ(zMax);
				}

				lOS.add(poly);

			}

			// For each edges we determine the minimal and maximal height in
			// order to generate vertical faces

			for (Arc a : g.getListeArcs()) {

				Face fd = a.getFaceDroite();
				Face fg = a.getFaceGauche();

				if (fd == null && fg == null) {
					continue;
				}

				double z1 = 0;
				double z2 = 0;

				if (fd == null || fd.isInfinite()) {

					System.out.println(fg.getArcsIgnores());

					z1 = Double.parseDouble(fg.getArcsIgnores());
					z2 = zMini;

				} else if (fg == null || fg.isInfinite()) {

					z1 = Double.parseDouble(fd.getArcsIgnores());
					z2 = zMini;

				} else {

					z1 = Double.parseDouble(fg.getArcsIgnores());
					z2 = Double.parseDouble(fd.getArcsIgnores());

				}

				double zMin = Math.min(z1, z2);
				double zMax = Math.max(z1, z2);

				if (zMax == zMini) {

					continue;
				}

				// if(Double.isNaN(zMin) || Double.isNaN(zMax)){

				// System.out.println("zMin : " + zMin + " zMAx " + zMax);

				// }

				// Extrusion of the geometry according to the considered height
				IGeometry geom = Extrusion2DObject.convertFromLine((ILineString) a.getGeometrie().clone(), zMin, zMax);

				lOS.addAll(FromGeomToSurface.convertGeom(geom));

			}

			
			/*
			for (IOrientableSurface os : lOS) {
				featCollOut.add(new DefaultFeature(os));
			}*/
			
			featCollOut.add(new DefaultFeature(new GM_MultiSurface<>(lOS)));

		}

		return featCollOut;
	}

	public static CarteTopo newCarteTopo(String name, IFeatureCollection<? extends IFeature> collection,
			double threshold) {

		try {
			// Initialisation d'une nouvelle CarteTopo
			CarteTopo carteTopo = new CarteTopo(name);
			carteTopo.setBuildInfiniteFace(false);
			// Récupération des arcs de la carteTopo
			IPopulation<Arc> arcs = carteTopo.getPopArcs();
			// Import des arcs de la collection dans la carteTopo
			for (IFeature feature : collection) {

				List<ILineString> lLLS = FromPolygonToLineString
						.convertPolToLineStrings((IPolygon) FromGeomToSurface.convertGeom(feature.getGeom()).get(0));

				for (ILineString ls : lLLS) {

					// affectation de la géométrie de l'objet issu de la
					// collection
					// à l'arc de la carteTopo
					for (int i = 0; i < ls.numPoints() - 1; i++) {
						// création d'un nouvel élément
						Arc arc = arcs.nouvelElement();
						arc.setGeometrie(new GM_LineString(ls.getControlPoint(i), ls.getControlPoint(i + 1)));
						// instanciation de la relation entre l'arc créé et
						// l'objet
						// issu de la collection
						arc.addCorrespondant(feature);
					}

				}

			}
			if (!test(carteTopo)) {
				logger.error("");
				System.exit(0);
			}
			carteTopo.creeNoeudsManquants(0.01);

			if (!test(carteTopo)) {
				logger.error("");
				System.exit(0);
			}

			carteTopo.fusionNoeuds(threshold);

			if (!test(carteTopo)) {
				logger.error("");
				System.exit(0);
			}

			carteTopo.decoupeArcs(0.1);
			carteTopo.splitEdgesWithPoints(0.1);

			carteTopo.filtreArcsDoublons();
			if (!test(carteTopo)) {
				logger.error("");
				System.exit(0);
			}

			// Création de la topologie Arcs Noeuds

			carteTopo.creeTopologieArcsNoeuds(threshold);
			// La carteTopo est rendue planaire
			if (!test(carteTopo)) {
				logger.error("");
				System.exit(0);
			}
			/*
			 * carteTopo.rendPlanaire(threshold); if (!test(carteTopo)) {
			 * logger.error(""); System.exit(0); }
			 */

			/*
			 * if (!test(carteTopo)) { System.out.println("Error 4"); }
			 * carteTopo.filtreArcsDoublons(); if (!test(carteTopo)) {
			 * System.out.println("Error 5"); }
			 * 
			 * 
			 * // DEBUG2.addAll(carteTopo.getListeArcs());
			 * 
			 * carteTopo.creeTopologieArcsNoeuds(threshold); if
			 * (!test(carteTopo)) { logger.error(""); System.exit(0); }
			 */

			/*
			 * if (!test(carteTopo)) { System.out.println("Error 6"); }
			 */

			// carteTopo.creeTopologieFaces();

			// carteTopo.filtreNoeudsSimples();
			// if (!test(carteTopo)) {
			// logger.error("");
			// System.exit(0);
			// }

			// DEBUG.addAll(carteTopo.getListeArcs());
			// Création des faces de la carteTopo
			carteTopo.creeTopologieFaces();
			if (!test(carteTopo)) {
				logger.error("");
				System.out.println("Error 3");
			}

			/*
			 * if (!test(carteTopo)) { System.out.println("Error 7"); }
			 */

			return carteTopo;

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	private static boolean test(CarteTopo ct) {
		for (Arc a : ct.getPopArcs()) {

			if (a.getGeometrie().coord().size() < 2) {
				return false;
			}

		}

		return true;

	}

	@SuppressWarnings("unused")
	private static double getMoyGroup(List<AbstractBuilding> lAB) {

		double moy = 0;

		for (AbstractBuilding aB : lAB) {

			moy = moy + getZMax(aB);

		}

		return moy / lAB.size();

	}

	public static List<AbstractBuilding> changeHeight(List<AbstractBuilding> lAB, double diffHeight,
			VeryFastRuleChecker vFR, CacheModelInstance<AbstractBuilding> cMI) {

		List<List<AbstractBuilding>> lLLAB = new ArrayList<>();

		// On prépare les groupes
		boucleab: for (AbstractBuilding aB : lAB) {

			Box3D b1 = new Box3D(aB.getGeom());

			for (List<AbstractBuilding> lABTemp : lLLAB) {

				for (AbstractBuilding aBTemp : lABTemp) {
					if (!aB.getFootprint().intersects(aBTemp.getFootprint())) {
						continue;
					}

					Box3D b2 = new Box3D(aBTemp.getGeom());

					double hi = b1.getURDP().getZ() - b1.getLLDP().getZ();
					double hj = b2.getURDP().getZ() - b2.getLLDP().getZ();

					if (Math.abs(hi - hj) > diffHeight) {
						continue;
					}

					lABTemp.add(aB);
					continue boucleab;

				}

			}

			List<AbstractBuilding> bTT = new ArrayList<>();
			bTT.add(aB);
			lLLAB.add(bTT);
		}

		// On prépare les géométries des groupes
		List<IGeometry> lGeomGroup = new ArrayList<>();
		for (List<AbstractBuilding> lABTemp : lLLAB) {

			int nbElem = lABTemp.size();
			IGeometry geom = lABTemp.get(0).getFootprint();

			for (int i = 1; i < nbElem; i++) {
				geom = geom.union(lABTemp.get(i).getFootprint());

			}

			lGeomGroup.add(geom);

		}

		// On fusionne les groupes
		int nbGroup = lLLAB.size();

		bouclei: for (int i = 0; i < nbGroup; i++) {

			List<AbstractBuilding> lABTemp1 = lLLAB.get(i);

			double hMini = Double.POSITIVE_INFINITY;
			double hMaxi = Double.NEGATIVE_INFINITY;

			for (AbstractBuilding aB : lABTemp1) {
				hMini = Math.min(hMini, getH(aB));
				hMaxi = Math.max(hMaxi, getH(aB));
			}

			for (int j = i + 1; j < nbGroup; j++) {

				List<AbstractBuilding> lABTemp2 = lLLAB.get(j);

				IGeometry geomi = lGeomGroup.get(i);
				IGeometry geomj = lGeomGroup.get(j);

				if (!geomi.intersects(geomj)) {
					continue;
				}
				double hMinj = Double.POSITIVE_INFINITY;
				double hMaxj = Double.NEGATIVE_INFINITY;

				for (AbstractBuilding aB : lABTemp2) {
					hMinj = Math.min(hMinj, getH(aB));
					hMaxj = Math.max(hMaxj, getH(aB));
				}

				// Intervalle en commun ?
				if ((hMinj <= hMaxi && hMinj >= hMini) || ((hMaxj >= hMini) && (hMaxj <= hMaxi))) {

					// on fusionne
					lGeomGroup.set(j, geomi.union(geomj));
					lABTemp2.addAll(lABTemp1);

					lGeomGroup.remove(i);
					lLLAB.remove(i);
					nbGroup--;
					i = -1;
					continue bouclei;

				}

			}

		}

		// On bouge les groupes

		List<AbstractBuilding> lABOUT = new ArrayList<>();
		for (List<AbstractBuilding> lABTem : lLLAB) {

			double hMini = Double.POSITIVE_INFINITY;
			double hMaxi = Double.NEGATIVE_INFINITY;

			for (AbstractBuilding aB : lABTem) {
				hMini = Math.min(hMini, getZMax(aB));
				hMaxi = Math.max(hMaxi, getZMax(aB));
			}

			// on tue tout ce qui existe

			List<AbstractBuilding> lDeath = lABTem;
			// cMI.update(new ArrayList<AbstractBuilding>(), lABTem);

			for (double d = hMaxi; d > 0; d = d - diffHeight / 2) {

				List<AbstractBuilding> lBorn = new ArrayList<>();

				for (AbstractBuilding aBT : lDeath) {

					lBorn.add(changeGeomZMax(aBT, d));

				}

				boolean check = vFR.check(cMI.update(lBorn, lDeath));

				System.out.println(check + " d  " + d);

				if (check) {
					lABTem.clear();
					lABOUT.addAll(lBorn);

					break;
				}

				lDeath = lBorn;

			}

		}

		return lABOUT;
	}

	public static AbstractBuilding changeGeomZMax(AbstractBuilding aBIni, double zMaxNew) {

		AbstractBuilding aB = (AbstractBuilding) aBIni.clone();

		Box3D b = new Box3D(aBIni.getGeom());

		double zMax = b.getURDP().getZ();

		IDirectPositionList dpl = aB.getGeom().coord();

		for (IDirectPosition dp : dpl) {

			if (dp.getZ() == zMax) {

				dp.setZ(zMaxNew);

			}

		}

		return aB;

	}

	public static double getZMax(AbstractBuilding aB) {
		Box3D b1 = new Box3D(aB.getGeom());

		return b1.getURDP().getZ();

	}

	public static double getH(AbstractBuilding aB) {
		Box3D b1 = new Box3D(aB.getGeom());

		return b1.getURDP().getZ() - b1.getLLDP().getZ();

	}

	public static List<AbstractBuilding> changeHeight2(List<AbstractBuilding> lAB, double diffHeight,
			VeryFastRuleChecker vFR, CacheModelInstance<AbstractBuilding> cMI) {

		int nbB = lAB.size();

		List<AbstractBuilding> lABBirth = new ArrayList<AbstractBuilding>();
		List<AbstractBuilding> lABDeath = new ArrayList<AbstractBuilding>();

		bouclei: for (int i = 0; i < nbB; i++) {

			AbstractBuilding aBi = lAB.get(i);

			for (int j = i + 1; j < nbB; j++) {

				if (i == 1 && j == 4) {
					System.out.println("je rame");
				}

				AbstractBuilding aBj = lAB.get(j);

				if (!aBi.getFootprint().intersects(aBj.getFootprint())) {
					continue;
				}

				Box3D b1 = new Box3D(aBi.getGeom());

				Box3D b2 = new Box3D(aBj.getGeom());

				double hi = b1.getURDP().getZ() - b1.getLLDP().getZ();
				double hj = b1.getURDP().getZ() - b2.getLLDP().getZ();

				if (hi == hj) {
					continue;
				}

				if (Math.abs(hi - hj) > diffHeight) {
					continue;
				}

				double hMax = Math.max(hi, hj);

				// on augmente la taille

				AbstractBuilding aBinew = changeGeomHMax(aBi, b1, hi, hMax);

				AbstractBuilding aBjnew = changeGeomHMax(aBj, b2, hj, hMax);

				if (aBinew != null) {
					lABBirth.add(aBinew);
					lABDeath.add(aBi);
				}

				if (aBjnew != null) {
					lABBirth.add(aBjnew);
					lABDeath.add(aBj);
				}

				boolean isCheck = vFR.check(cMI.update(lABBirth, lABDeath));

				if (isCheck) {

					if (aBjnew != null) {
						lAB.remove(j);
						lAB.add(aBjnew);

					}

					if (aBinew != null) {
						lAB.remove(i);
						lAB.add(aBinew);

					}

					lABBirth.clear();
					lABDeath.clear();

					i = -1;
					continue bouclei;
				}

				// on annule
				cMI.update(lABDeath, lABBirth);
				lABBirth.clear();
				lABDeath.clear();

				hMax = Math.min(hi, hj);

				// on diminue la taille

				aBinew = changeGeomHMax(aBi, b1, hi, hMax);

				aBjnew = changeGeomHMax(aBj, b2, hj, hMax);

				if (aBinew != null) {
					lABBirth.add(aBinew);
					lABDeath.add(aBi);
				}

				if (aBjnew != null) {
					lABBirth.add(aBjnew);
					lABDeath.add(aBj);
				}

				isCheck = vFR.check(cMI.update(lABBirth, lABDeath));

				if (isCheck) {
					if (aBjnew != null) {
						lAB.remove(j);
						lAB.add(aBjnew);

					}

					if (aBinew != null) {
						lAB.remove(i);
						lAB.add(aBinew);

					}

					i = -1;
					continue bouclei;
				}

				// on annule
				cMI.update(lABDeath, lABBirth);
				lABBirth.clear();
				lABDeath.clear();

				// les modifs ne sont pas acceptée, on revient à l'origine

			}

		}

		return lAB;
	}

	public static AbstractBuilding changeGeomHMax(AbstractBuilding aBIni, Box3D b, double currentH, double newH) {

		AbstractBuilding aB = (AbstractBuilding) aBIni.clone();

		double zMin = b.getLLDP().getZ();
		double zMax = b.getURDP().getZ();

		IDirectPositionList dpl = aB.getGeom().coord();

		for (IDirectPosition dp : dpl) {

			if (dp.getZ() == zMax) {

				dp.setZ(zMin + newH);

			}

		}

		return aB;

	}

}
