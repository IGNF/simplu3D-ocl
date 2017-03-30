package fr.ign.cogit.simplu3d.checker;

import java.util.ArrayList;
import java.util.List;

import fr.ign.cogit.simplu3d.importer.model.ImportModelInstanceBasicPropertyUnit;
import fr.ign.cogit.simplu3d.model.BasicPropertyUnit;
import fr.ign.cogit.simplu3d.model.CadastralParcel;
import fr.ign.cogit.simplu3d.model.EnvironnementOCL;
import fr.ign.cogit.simplu3d.model.RuleOCL;
import fr.ign.cogit.simplu3d.model.SubParcel;
import fr.ign.cogit.simplu3d.model.UrbaZoneOCL;
import tudresden.ocl20.pivot.essentialocl.standardlibrary.OclBoolean;
import tudresden.ocl20.pivot.interpreter.IInterpretationResult;
import tudresden.ocl20.pivot.interpreter.IOclInterpreter;
import tudresden.ocl20.pivot.interpreter.OclInterpreterPlugin;
import tudresden.ocl20.pivot.modelinstance.IModelInstance;
import tudresden.ocl20.pivot.modelinstancetype.exception.TypeNotFoundInModelException;
import tudresden.ocl20.pivot.modelinstancetype.types.IModelInstanceElement;
import tudresden.ocl20.pivot.modelinstancetype.types.IModelInstanceObject;
import tudresden.ocl20.pivot.pivotmodel.Constraint;
import tudresden.ocl20.pivot.simplu3d.OCLInterpreterSimplu3D;

/**
 * 
 *        This software is released under the licence CeCILL
 * 
 *        see LICENSE.TXT
 * 
 *        see <http://www.cecill.info/ http://www.cecill.info/
 * 
 * 
 * 
 * @copyright IGN
 * 
 * @author Brasebin Mickaël
 * 
 * @version 1.0
 *
 * Classe permettant la vérification des règles d'urbanisme sur une unité foncière
 */
public class VeryFastRuleChecker {

  private List<SubParcel> sPList = new ArrayList<SubParcel>();
  private List<IModelInstanceObject> lRelevantObjects = new ArrayList<>();
  private List<IOclInterpreter> lModelInterpreter = new ArrayList<>();
  private List<IModelInstance> lModelInstance = new ArrayList<>();

  public int evalCount = 0;
  public int evalFalse = 0;

  public List<List<Integer>> lFalseArray = new ArrayList<>();

  public int getEvalCount() {
    return evalCount;
  }

  public int getEvalFalse() {
    return evalFalse;
  }

  public List<List<Integer>> getlFalseArray() {
    return lFalseArray;
  }

  public VeryFastRuleChecker(BasicPropertyUnit bPU, UrbaZoneOCL uz) {
    this.bPU = bPU;
    this.uz = uz;
    init(bPU);
  }

  private BasicPropertyUnit bPU;
  private UrbaZoneOCL uz;


  public boolean check(List<IModelInstanceObject> newBuildings) {
    // System.out.println(lModeInstance.get(0).getAllModelInstanceObjects().size());
    /*
     * for(Type t : lModeInstance.get(0).getAllImplementedTypes()){
     * System.out.println("Type : " + t + "  "
     * +lModeInstance.get(0).getAllInstances(t).size()); }
     */
    int numberOfSubParcels = sPList.size();
    for (int sPIndex = 0; sPIndex < numberOfSubParcels; sPIndex++) {
      evalCount++;
      int count = 0;
      for (RuleOCL rule : uz.getRules()) {
        for (IModelInstanceObject imiObject : lRelevantObjects) {
          boolean isOk = interpret(imiObject, lModelInterpreter.get(sPIndex), rule.constraint);
          if (!isOk) {
            lFalseArray.get(sPIndex).set(count, lFalseArray.get(sPIndex).get(count) + 1);
            evalFalse++;
            return false;
          }
        }
        for (IModelInstanceObject imiObject : newBuildings) {
          boolean isOk = interpret(imiObject, lModelInterpreter.get(sPIndex), rule.constraint);
          if (!isOk) {
            lFalseArray.get(sPIndex).set(count, lFalseArray.get(sPIndex).get(count) + 1);
            evalFalse++;
            return false;
          }
        }
        count++;
      }
    }
    return true;
  }

  private void init(BasicPropertyUnit bPU) {
    new OclInterpreterPlugin();
    for (CadastralParcel cP : bPU.getCadastralParcels()) {
      for (SubParcel sP : cP.getSubParcels()) {
        sPList.add(sP);
        IModelInstance iM = ImportModelInstanceBasicPropertyUnit
            .generateModelInstance(EnvironnementOCL.getModel());
        lModelInstance.add(iM);
        try {
          lRelevantObjects.add(ImportModelInstanceBasicPropertyUnit
              .importCadastralSubParcel(iM, sP));
          lRelevantObjects.add((IModelInstanceObject) iM.addModelInstanceElement(bPU));
          ImportModelInstanceBasicPropertyUnit.importCadastralParcel(iM, cP);
        } catch (TypeNotFoundInModelException e) {
          e.printStackTrace();
        }
        lModelInterpreter.add(new OCLInterpreterSimplu3D(iM));
      }
    }
    int nbInt = sPList.size();
    for (int i = 0; i < nbInt; i++) {
      lFalseArray.add(new ArrayList<Integer>());
      int sizeTemp = 	uz.getRules().size();
      for (int j = 0; j < sizeTemp; j++) {
        lFalseArray.get(i).add(0);
      }
    }
  }

  public boolean interpret(IModelInstanceElement imiObject, IOclInterpreter interpret, Constraint c) {
    IInterpretationResult result = null;
    try {
      result = interpret.interpretConstraint(c, imiObject);
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Interpret exit");
      System.exit(0);
    }
    if (result != null) {
      if (result.getResult() instanceof OclBoolean) {
        OclBoolean bool = (OclBoolean) result.getResult();
        if (bool.oclIsInvalid().isTrue()) {
          System.out.println("  " + result.getModelObject() + " ("
              + result.getConstraint().getKind() + ": "
              + result.getConstraint().getSpecification().getBody() + "): " + result.getResult());
        }
        if (!bool.isTrue()) {
          // System.out.println("Règle non vérifiée");
          // System.out.println(imiObject);
          
        //   System.out.println("  " + result.getModelObject() + " (" +
          //   result.getConstraint().getKind() + ": " +
          //  result.getConstraint().getSpecification().getBody() + "): " +
          // result.getResult());
           
          return false;
        }
        
        System.out.println("  " + result.getModelObject() + " (" +
        result.getConstraint().getKind() + ": " +
        result.getConstraint().getSpecification().getBody() + "): " +
        result.getResult());
        
        
      } else {
        System.out.println("  " + result.getModelObject() + " (" + result.getConstraint().getKind()
            + ": " + result.getConstraint().getSpecification().getBody() + "): "
            + result.getResult());
      }
    }
    return true;
  }

  public BasicPropertyUnit getbPU() {
    return bPU;
  }

  public List<IModelInstance> getlModeInstance() {
    return lModelInstance;
  }

  public List<SubParcel> getsPList() {
    return sPList;
  }

}
