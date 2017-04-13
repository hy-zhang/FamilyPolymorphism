package lombok.eclipse.handlers.family;

import java.util.*;

import static lombok.eclipse.handlers.EclipseHandlerUtil.*;

import org.eclipse.jdt.internal.compiler.ast.*;

import lombok.eclipse.EclipseNode;
import lombok.eclipse.handlers.family.util.*;

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
		
		TypeReference returnType;
		String methodName;
		List<TypeReference> paramsType;
		boolean isDefault;
		
		TypeReference origin;
		
		Method(TypeReference returnType, String methodName, List<TypeReference> paramsType,
				boolean isDefault, TypeReference origin) {
			this.returnType = returnType;
			this.methodName = methodName;
			this.paramsType = paramsType;
			this.isDefault = isDefault;
			this.origin = origin;
		}
		
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
		
		private List<Method> getAllMethods(EclipseNode thisNode, boolean root, TypeReference[] typeArguments) {
			if (!(thisNode.get() instanceof TypeDeclaration)) return null;
			TypeDeclaration thisDecl = (TypeDeclaration) thisNode.get();
			if (!General.isInterface(thisDecl)) return null;
			
			Map<String, TypeReference> instantiation = new HashMap<String, TypeReference>();
			String[] paramNames = General.getTypeParameterNames(thisDecl);
			for (int i = 0; i < paramNames.length; i++) {
				instantiation.put(paramNames[i], copyType(typeArguments[i]));
			}
			
			List<Method> res = new ArrayList<Method>();
			
			TypeReference origin = null; // TODO
			
			if (thisDecl.superInterfaces != null) {
				for (TypeReference superInterface : thisDecl.superInterfaces) {
					TypeReference[] newTypeArguments = General.getTypeArguments(superInterface);
					for (int i = 0; i < newTypeArguments.length; i++)
						newTypeArguments[i] = General.instantiateTypeReference(newTypeArguments[i], instantiation);
					EclipseNode superInterfaceDecl = Path.getTypeDecl(superInterface.toString(), thisNode);
					res.addAll(getAllMethods(superInterfaceDecl, false, newTypeArguments));					
				}
			}
			
			if (!root) {
				for (EclipseNode method : thisNode.down()) {
					if (!(method.get() instanceof MethodDeclaration)) continue;
					MethodDeclaration methodDecl = (MethodDeclaration) method.get();
					if (methodDecl.isStatic()) continue;
					
					// Not supported for now.
					if (methodDecl.typeParameters != null) continue;
					
					List<TypeReference> paramsType = new ArrayList<TypeReference>();
					if (methodDecl.arguments != null) {
						for (Argument arg : methodDecl.arguments) {
							paramsType.add(copyType(arg.type));
						}
					}
					
					Method m = new Method(copyType(methodDecl.returnType), String.valueOf(methodDecl.selector), paramsType,
							methodDecl.isDefaultMethod(), origin);
					res.add(m);					
				}
			} else {
				// TODO
			}
			
			return null;
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
				if (!Subtype.subType(m.returnType, compilationUnit, existingMethod.returnType, compilationUnit))
					return false;
			}
			return true;
		}
		
		private Method mostSpecific(List<Method> ms) {
			if (ms.isEmpty()) return null;
			Method res = ms.get(0);
			if (ms.size() == 1) return res;
			if (res.isDefault) return null;
			for (int i = 1; i < ms.size(); i++) {
				Method m = ms.get(i);
				if (m.isDefault) return null;
				if (Subtype.subType(res.returnType, compilationUnit, m.returnType, compilationUnit)) continue;
				if (Subtype.subType(m.returnType, compilationUnit, res.returnType, compilationUnit)) res = m;
				else return null;
			}
			return res;
		}
	}
	
}
