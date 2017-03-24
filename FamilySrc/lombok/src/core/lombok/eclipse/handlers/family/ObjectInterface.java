package lombok.eclipse.handlers.family;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;

import lombok.eclipse.EclipseNode;
import lombok.eclipse.handlers.family.util.General;
import lombok.eclipse.handlers.family.util.Path;
import lombok.eclipse.handlers.family.util.Subtype;

public class ObjectInterface {
	
	/**
	 * The EclipseNode annotated with @Family.
	 * Initialized by constructor.
	 * Stands for the family interface.
	 */
	EclipseNode node;
	
	/**
	 * The @Family annotation.
	 * For throwing errors/warnings.
	 */
	EclipseNode annotationNode;

	/**
	 * Annotation ASTNode. Often used for copying types.
	 */
	ASTNode ast;
	
	/**
	 * Locating generated code.
	 */
	long p;
	
	TypeDeclaration meDecl;
	Map<String, Field> fields = new HashMap<String, Field>();
	Map<String, Method> methods = new HashMap<String, Method>();
	
	/**
	 * Constructor.
	 * @param node
	 * @param annotationNode
	 * @param p
	 */	
	public ObjectInterface(EclipseNode node, EclipseNode annotationNode, Annotation ast, long p) {
		this.node = node;
		this.annotationNode = annotationNode;
		this.ast = ast;
		this.p = p;
		
		if (!(this.node.get() instanceof TypeDeclaration)) return;
		this.meDecl = (TypeDeclaration) this.node.get();
	}
	
	/**
	 * Used for debugging conveniently.
	 * For complicated work, debug.log() is recommended for logging into file.
	 * This method shows message as a warning at the annotation.
	 * @param msg: message
	 */
	private void println(String msg) {
		annotationNode.addWarning(msg + "\n");
	}
	
	private boolean canGenerateOf() {
		
		
		return true;
	}
	
	private static class Field {
		
	}
	
	private static class Method {
		
		MethodDeclaration decl;
		TypeReference origin;
		
		boolean equals(Method m) {
			return false;
		}
		
	}
	
	private static class MBody {
		
		EclipseNode node;
		TypeDeclaration nodeDecl;
		EclipseNode compilationUnit;
		boolean mBody = false;
		
		MBody(EclipseNode node, EclipseNode compilationUnit) {
			if (!(node.get() instanceof TypeDeclaration)) return;
			this.node = node;
			this.nodeDecl = (TypeDeclaration) node.get();
			if (!General.isInterface(this.nodeDecl)) return;
			this.compilationUnit = compilationUnit;
			
			
			this.mBody = true;
		}
		
		private void getAllMethods(EclipseNode thisNode, boolean root) {
			if (!(thisNode.get() instanceof TypeDeclaration)) return;
			TypeDeclaration thisDecl = (TypeDeclaration) thisNode.get();
			if (!General.isInterface(thisDecl)) return;
			
			if (thisDecl.superInterfaces != null) {
				for (TypeReference superInterface : thisDecl.superInterfaces) {
					getAllMethods(Path.getTypeDecl(superInterface.toString(), thisNode), false);
					// trafo: instantiation
					
				}
			}
			
			if (!root) {
				
			} else {
				
			}
		}
		
		private Map<Method, List<Method>> shadow(List<Method> allMethods) {
			Map<Method, List<Method>> res = new HashMap<Method, List<Method>>();
			for (Method toAdd : allMethods) {
				boolean addedToRes = false;
				for (Method resKey : res.keySet()) {
					if (resKey.equals(toAdd)) {
						List<Method> oldResValue = res.get(resKey);
						List<Method> newResValue = new ArrayList<Method>();
						boolean toAddToNewResValue = true;
						for (Method existingMethod : oldResValue) {
							if (Subtype.subType(existingMethod.origin, compilationUnit, toAdd.origin, compilationUnit)) {
								toAddToNewResValue = false;
								break;
							}
							if (!Subtype.subType(toAdd.origin, compilationUnit, existingMethod.origin, compilationUnit)) {
								newResValue.add(existingMethod);
							}
						}
						if (toAddToNewResValue) {newResValue.add(toAdd); res.put(resKey, newResValue);}
						addedToRes = true;
						break;
					}
				}
				if (!addedToRes) {
					List<Method> singleton = new ArrayList<Method>();
					singleton.add(toAdd);
					res.put(toAdd, singleton);
				}
			}
			return res;
		}
		
		private boolean canOverride(Method m, List<Method> ms) {
			for (Method existingMethod : ms) {
				if (!Subtype.subType(m.decl.returnType, compilationUnit, existingMethod.decl.returnType, compilationUnit))
					return false;
			}
			return true;
		}
		
		private Method mostSpecific(List<Method> ms) {
			if (ms.isEmpty()) return null;
			Method res = ms.get(0);
			if (ms.size() == 1) return res;
			if (res.decl.isDefaultMethod()) return null;
			for (int i = 1; i < ms.size(); i++) {
				Method m = ms.get(i);
				if (m.decl.isDefaultMethod()) return null;
				if (Subtype.subType(res.decl.returnType, compilationUnit, m.decl.returnType, compilationUnit)) continue;
				if (Subtype.subType(m.decl.returnType, compilationUnit, res.decl.returnType, compilationUnit)) res = m;
				else return null;
			}
			return res;
		}
	}
	
}
