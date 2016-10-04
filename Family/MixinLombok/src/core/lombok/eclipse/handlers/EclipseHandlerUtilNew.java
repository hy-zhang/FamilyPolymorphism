package lombok.eclipse.handlers;

import static lombok.eclipse.Eclipse.ECLIPSE_DO_NOT_TOUCH_FLAG;
import static lombok.eclipse.handlers.EclipseHandlerUtil.*;

import java.util.*;

import lombok.eclipse.*;

import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.*;

import java.io.*;

public class EclipseHandlerUtilNew {}

final class Util {
	/* Check if [here1].t1 is a subtype of [here2].t2. */
	static boolean subType(TypeReference t1, EclipseNode here1, TypeReference t2, EclipseNode here2) { // To check.
		// The code below can be improved by checking case A<T> <: A<W> iff T = W.
		if (sameType(t1, here1, t2, here2)) return true;
		EclipseNode d1 = Path.getTypeDecl(getRefName(t1), here1);
		if (d1 == null) return false;
		if (!(d1.get() instanceof TypeDeclaration)) return false;
		TypeDeclaration decl = (TypeDeclaration) d1.get();
		if (decl.superclass != null && subType(decl.superclass, d1, t2, here2)) return true;
		if (decl.superInterfaces != null) {
			TypeReference[] t1Args = getTypeArgsFromType(t1);
			HashMap<String, TypeReference> tyMap = new HashMap<String, TypeReference>();
			if (decl.typeParameters != null) {
				for (int i = 0; i < decl.typeParameters.length; i++)
					tyMap.put(String.valueOf(decl.typeParameters[i].name), t1Args[i]);
			}
			for (TypeReference x : ((TypeDeclaration) d1.get()).superInterfaces) {
				TypeReference t1_new = copyType(x);
				if (x instanceof ParameterizedSingleTypeReference || x instanceof ParameterizedQualifiedTypeReference) 
					t1_new = replaceRef(x, tyMap);
				if (subType(t1_new, d1, t2, here2)) return true;
			}
		}
		return false;
	}
	/* Check if [here1].t1 and [here2].t2 have the same type. */
	static boolean sameType(TypeReference t1, EclipseNode here1, TypeReference t2, EclipseNode here2) { // To check.
		EclipseNode decl1 = Path.getTypeDecl(getRefName(t1), here1);
		EclipseNode decl2 = Path.getTypeDecl(getRefName(t2), here2);
		if (decl1 == null && decl2 != null) return false;
		if (decl1 != null && decl2 == null) return false;
		if (decl1 == null && decl2 == null) return getRefName(t1).equals(getRefName(t2));
		String path1 = Path.getAbsolutePathForType(Path.getTypeDecl(getRefName(t1), here1));
		String path2 = Path.getAbsolutePathForType(Path.getTypeDecl(getRefName(t2), here2));
		if (!path1.equals(path2)) return false;
		TypeReference[] t1Args = getTypeArgsFromType(t1), t2Args = getTypeArgsFromType(t2);
		if (t1Args == null) return t2Args == null;
		if (t2Args == null || t1Args.length != t2Args.length) return false;
		for (int i = 0; i < t1Args.length; i++)
			if (!sameType(t1Args[i], here1, t2Args[i], here2)) return false;
		return true;
	}
	/* Check if two global type references t1 and t2 have the same type. */
	static boolean sameTypeG(TypeReference t1, TypeReference t2) {
		return getRefName(t1).equals(getRefName(t2));
	}
	static TypeReference[] getTypeArgsFromType(TypeReference t) {
		TypeReference[] res = null;
		if (t instanceof ParameterizedSingleTypeReference) {
			res = new TypeReference[((ParameterizedSingleTypeReference) t).typeArguments.length];
			for (int i = 0; i < res.length; i++) res[i] = copyType(((ParameterizedSingleTypeReference) t).typeArguments[i]);
		} else if (t instanceof ParameterizedQualifiedTypeReference) {
			int len = ((ParameterizedQualifiedTypeReference) t).typeArguments.length;
			res = new TypeReference[((ParameterizedQualifiedTypeReference) t).typeArguments[len - 1].length];
			for (int i = 0; i < res.length; i++) res[i] = copyType(((ParameterizedQualifiedTypeReference) t).typeArguments[len - 1][i]);
		}
		return res;
	}
	/* Check if t is an interface. */
	static boolean isInterface(TypeDeclaration t) {
		return (t.modifiers & ClassFileConstants.AccInterface) != 0;
	}
	/*  Check if m is a field method. */
	static boolean isField(MethodDeclaration m) {
		if (m.isDefaultMethod() || m.isStatic() || isVoidMethod(m)) return false;
		if (m.arguments != null && m.arguments.length != 0) return false;
		if (m.selector.length < 2) return false;
		if (m.selector[0] != '_') return false;
		return true;
	}
	/* Check if m is an abstract method. */
	static boolean isAbstractMethod(MethodDeclaration m) {
		return !m.isDefaultMethod() && !m.isStatic();	
	}
	/* Check if m is a void method. */
	static boolean isVoidMethod(MethodDeclaration m) {
		if (!(m.returnType instanceof SingleTypeReference)) return false;
		return Arrays.equals(TypeConstants.VOID, ((SingleTypeReference) m.returnType).token);
	}
	/* Change the first letter of s to upper case. */
	static String toUpperCase(String s) {
		return s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1, s.length());
	}
	/* Change the first letter of s to lower case. */
	static String toLowerCase(String s) {
		return s.isEmpty() ? s : s.substring(0, 1).toLowerCase() + s.substring(1, s.length());
	}
	/* TypeReference to String. */
	static String getRefName(TypeReference t) {
		if (t instanceof SingleTypeReference) return String.valueOf(((SingleTypeReference) t).token);
		if (t instanceof QualifiedTypeReference) {
			String res = "";
			char[][] names = ((QualifiedTypeReference) t).tokens;
			for (int i = 0; i < names.length; i++) {
				res += String.valueOf(names[i]);
				if (i != names.length - 1) res += ".";
			}
			return res;
		}
		return null; // Exceptions for other cases.
	}
	/* String to TypeReference. */
	static TypeReference getNameRef(String name, long p) {
		String[] names = name.split("\\.");
		if (names.length == 1) return new SingleTypeReference(name.toCharArray(), p);
		char[][] tokens = new char[names.length][];
		long[] ps = new long[names.length];
		for (int i = 0; i < tokens.length; i++) {
			tokens[i] =  names[i].toCharArray();
			ps[i] = p;
		}
		return new QualifiedTypeReference(tokens, ps);
	}
	/* Update a type name by its absolute path. */
	static TypeReference updateRef(TypeReference t, EclipseNode here, long p) {
		EclipseNode typeDecl = Path.getTypeDecl(getRefName(t), here); // Critical: potential name conflicts.
		if (typeDecl == null) return t; // Can be either an unbounded type or a primitive type. User is supposed to check correctness.
		TypeReference res = getNameRef(Path.getAbsolutePathForType(typeDecl), p);
		TypeReference[] typeArgs = null;
		if (t instanceof ParameterizedSingleTypeReference) {
			TypeReference[] oldArgs = ((ParameterizedSingleTypeReference) t).typeArguments;
			typeArgs = new TypeReference[oldArgs.length];
			for (int i = 0; i < typeArgs.length; i++) typeArgs[i] = copyType(oldArgs[i]); // No need to update them?
		} else if (t instanceof ParameterizedQualifiedTypeReference) {
			TypeReference[] oldArgs = ((ParameterizedQualifiedTypeReference) t).typeArguments[((ParameterizedQualifiedTypeReference) t).typeArguments.length - 1];
			typeArgs = new TypeReference[oldArgs.length];
			for (int i = 0; i < typeArgs.length; i++) typeArgs[i] = copyType(oldArgs[i]); // No need to update them?
		}
		if (res instanceof SingleTypeReference) {
			SingleTypeReference tempRes = (SingleTypeReference) res;
			if (typeArgs != null) return new ParameterizedSingleTypeReference(tempRes.token, typeArgs, 0, p);
			else return res;
		} else if (res instanceof QualifiedTypeReference) {
			QualifiedTypeReference tempRes = (QualifiedTypeReference) res;
			long[] ps = new long[tempRes.tokens.length];
			for (int i = 0; i < ps.length; i++) ps[i] = p;
			if (typeArgs != null) {
				TypeReference[][] typeArgs2 = new TypeReference[tempRes.tokens.length][];
				for (int i = 0; i < typeArgs2.length; i++) typeArgs2[i] = null;
				typeArgs2[typeArgs2.length - 1] = typeArgs;
				return new ParameterizedQualifiedTypeReference(tempRes.tokens, typeArgs2, 0, ps);
			} else return res;
		}
		return null;
	}
	/* Update a method with all types inside updated. */
	static MethodDeclaration updateMDecl(MethodDeclaration m, EclipseNode here, ASTNode _ast, long p) { // Warning: unsafe, in Java all objects are references.
		MethodDeclaration newDecl = copyMethod(m, _ast, p);
		newDecl.returnType = updateRef(newDecl.returnType, here, p);
		if (newDecl.arguments != null) {
			for (int i = 0; i < newDecl.arguments.length; i++)
				newDecl.arguments[i].type = updateRef(newDecl.arguments[i].type, here, p);
		}
		return newDecl;
	}
	/* Update a type with type arguments. */
	static TypeReference replaceRef(TypeReference t, HashMap<String, TypeReference> map) {
		TypeReference res = copyType(t);
		if (res instanceof ParameterizedSingleTypeReference) {
			ParameterizedSingleTypeReference res2 = (ParameterizedSingleTypeReference) res;
			for (int i = 0; i < res2.typeArguments.length; i++)
				res2.typeArguments[i] = replaceRef(res2.typeArguments[i], map);
			return res;
		} else if (res instanceof ParameterizedQualifiedTypeReference) {
			ParameterizedQualifiedTypeReference res2 = (ParameterizedQualifiedTypeReference) res;
			for (int i = 0; i < res2.typeArguments[res2.typeArguments.length - 1].length; i++)
				res2.typeArguments[res2.typeArguments.length - 1][i] = replaceRef(res2.typeArguments[res2.typeArguments.length - 1][i], map);
			return res;
		} else if (res instanceof SingleTypeReference) { // Name conflicts maybe?
			SingleTypeReference res2 = (SingleTypeReference) res;
			String name = String.valueOf(res2.token);
			if (map.containsKey(name)) return map.get(name);
			else return res;
		} else return res;
	}
	/* Copy a method. Used for updateMDecl(). */
	private static MethodDeclaration copyMethod(MethodDeclaration m, ASTNode _ast, long p) {
		MethodDeclaration res = new MethodDeclaration(m.compilationResult);
		if (m.arguments != null) {
			res.arguments = new Argument[m.arguments.length];
			for (int i = 0; i < m.arguments.length; i++)
				res.arguments[i] = new Argument(String.valueOf(m.arguments[i].name).toCharArray(), p, copyType(m.arguments[i].type), m.arguments[i].modifiers);
		}
		res.bits = m.bits;
		res.modifiers = m.modifiers;
		res.returnType = copyType(m.returnType);
		res.typeParameters = copyTypeParams(m.typeParameters, _ast);
		res.selector = String.valueOf(m.selector).toCharArray();
		return res;
	}
	
	/* Create a new method. */
	static MethodDeclaration newMethod(CompilationResult comp) {
		MethodDeclaration method = new MethodDeclaration(comp);
		method.annotations = null;
		method.modifiers = ClassFileConstants.AccDefault;
		method.typeParameters = null;
		method.returnType = null;
		method.selector = "".toCharArray();
		method.arguments = null;
		method.binding = null;
		method.thrownExceptions = null;
		method.bits |= ECLIPSE_DO_NOT_TOUCH_FLAG;
		method.statements = null;
		return method;
	}
	/* Create a new interface. */
	static TypeDeclaration newType(String name, CompilationResult comp) {
		TypeDeclaration res = new TypeDeclaration(comp);
		res.bits |= Eclipse.ECLIPSE_DO_NOT_TOUCH_FLAG;
		res.modifiers = ClassFileConstants.AccDefault | ClassFileConstants.AccInterface;
		res.name = name.toCharArray();
		return res;
	}
	
	static class Path {
		/* Function "Exact" in the formalization.
		 * Attention: not [here].name, but "name" is used AT NODE "here".
		 * But in the formalization, Exact(I, T) stands for [T].I
		 * The difference makes this method also useful in other cases. */
		static EclipseNode getTypeDecl(String name, EclipseNode here) {
			if (!(here.get() instanceof TypeDeclaration || here.get() instanceof MethodDeclaration)) return getTypeDeclFromAbsolutePath(name, here);
			int index = name.lastIndexOf('.');
			if (index > -1) return getTypeDecl2(name.substring(index + 1, name.length()), getTypeDecl(name.substring(0, index), here));
			EclipseNode p = here;
			if (p.get() instanceof MethodDeclaration) p = p.up();
			p = p.up();
			while (true) {
				EclipseNode res = getTypeDecl2(name, p);
				if (res != null) return res;
				if (p.get() instanceof TypeDeclaration) p = p.up();
				else break;
			}
			return null;
		}
		/* Function "Exact2" in the formalization.
		 * Attention: getTypeDecl for [outside].name */
		private static EclipseNode getTypeDecl2(String name, EclipseNode outside) {
			if (!(outside.get() instanceof TypeDeclaration)) return getChild(outside, name);
			EclipseNode isChild = getChild(outside, name);
			if (isChild != null) return isChild;
			TypeDeclaration decl = (TypeDeclaration) outside.get();
			EclipseNode res = null;
			String resStr = "";
			if (decl.superInterfaces == null) return null;
			for (TypeReference x : decl.superInterfaces) {
				EclipseNode resX = getTypeDecl2(name, getTypeDecl(getRefName(x), outside));
				if (resX == null) continue;
				String resXStr = getAbsolutePathForType(resX);
				if (res == null) { res = resX; resStr = resXStr; }
				else if (!resStr.equals(resXStr)) return null; // conflict. 
			}
			return res;
		}
		/* Get type declaration from absolute path. */
		static EclipseNode getTypeDeclFromAbsolutePath(String name, EclipseNode compilationUnit) {
			String[] names = name.split("\\.");
			EclipseNode res = getTypeDecl2(names[0], compilationUnit);
			for (int i = 1; i < names.length; i++) res = getTypeDecl2(names[i], res);
			return res;
		}
		/* Get direct child of "node" with name "child". */
		static EclipseNode getChild(EclipseNode node, String child) {
			for (EclipseNode x : node.down()) {
				if (!(x.get() instanceof TypeDeclaration)) continue;
				TypeDeclaration y = (TypeDeclaration) x.get();
				if (!Util.isInterface(y)) continue;
				if (String.valueOf(y.name).equals(child)) return x;
			}
			return null;
		}	
		/* Get the absolute path of node (including node itself if it's an interface declaration). */
		static String getAbsolutePathForType(EclipseNode node) {
			String res = "";
			EclipseNode p = node;
			if (p != null && (p.get() instanceof MethodDeclaration)) p = p.up();
			while (p.up() != null) {
				if (!(p.get() instanceof TypeDeclaration)) break;
				TypeDeclaration decl = (TypeDeclaration) p.get();
				if (!res.isEmpty()) res = "." + res;
				res = String.valueOf(decl.name) + res;
				p = p.up();
			}
			return res;
		}
	}
	static class MBody {
		private long p;
		private EclipseNode comp;
		ASTNode _ast;
		MBody(long p, EclipseNode compilationUnit, ASTNode _ast) { this.p = p; this.comp = compilationUnit; this._ast = _ast; }		
		/* Calculate mbody(node). All specific methods included. */
		public Type mBody(EclipseNode node) {
			if (!(node.get() instanceof TypeDeclaration)) return null;
			TypeDeclaration decl = (TypeDeclaration) node.get();
			if (!isInterface(decl)) return null;
			
			// Haoyuan.
			TypeReference[] typeParams = null;
			if (decl.typeParameters != null) {
				typeParams = new TypeReference[decl.typeParameters.length];
					for (int i = 0; i < typeParams.length; i++) typeParams[i] = new SingleTypeReference(decl.typeParameters[i].name, p);
			}
			ArrayList<Method> allMethods = collectAllMethods(node, typeParams, true);
			if (allMethods == null) return null;
			return new Type(allMethods.toArray(new Method[allMethods.size()]));
		}
		/* Collect all methods at current node. Recursion is used, when root is true, result is returned.
		 * Warning: Didn't take static methods into consideration. */
		private ArrayList<Method> collectAllMethods(EclipseNode node, TypeReference[] typeParams, boolean root) {
			if (!(node.get() instanceof TypeDeclaration)) return null;
			TypeDeclaration decl = (TypeDeclaration) node.get();
			TypeReference declRef = getNameRef(Path.getAbsolutePathForType(node), p);
			if (!Util.isInterface(decl)) return null;
			
			// Haoyuan.
			HashMap<String, TypeReference> paramsMap = new HashMap<String, TypeReference>();
			if (decl.typeParameters != null) {
				for (int i = 0; i < decl.typeParameters.length; i++)
					paramsMap.put(String.valueOf(decl.typeParameters[i].name), typeParams[i]);
			}
			ArrayList<Method> allMethods = new ArrayList<Method>();		
			if (decl.superInterfaces != null) {
				for (int i = 0; i < decl.superInterfaces.length; i++) {
					
					// Haoyuan.
					TypeReference tempT = decl.superInterfaces[i];
					TypeReference[] newTypeParams = null; // Only the case A.B.C<P, Q, R> makes sense.
					if (tempT instanceof ParameterizedSingleTypeReference) {
						ParameterizedSingleTypeReference tempTT = (ParameterizedSingleTypeReference) tempT;
						newTypeParams = new TypeReference[tempTT.typeArguments.length];
						for (int k = 0; k < newTypeParams.length; k++) newTypeParams[k] = copyType(replaceRef(tempTT.typeArguments[k], paramsMap));
					} else if (tempT instanceof ParameterizedQualifiedTypeReference) {
						ParameterizedQualifiedTypeReference tempTT = (ParameterizedQualifiedTypeReference) tempT;
						newTypeParams = new TypeReference[tempTT.typeArguments[tempTT.typeArguments.length - 1].length];
						for (int k = 0; k < newTypeParams.length; k++) newTypeParams[k] = copyType(replaceRef(tempTT.typeArguments[tempTT.typeArguments.length - 1][k], paramsMap));
					}

					ArrayList<Method> getMethods = collectAllMethods(Path.getTypeDecl(Printer.toString(tempT), node), newTypeParams, false);
					
					allMethods.addAll(getMethods);
				}
			}
			if (!root) { // Attention: we need to know the full qualified type reference.
				for (EclipseNode x : node.down()) {
					if (!(x.get() instanceof MethodDeclaration)) continue;
					
					// Haoyuan.
					MethodDeclaration copy = copyMethod((MethodDeclaration) x.get(), _ast, p);
					if (decl.typeParameters != null) {
						copy.returnType = Util.replaceRef(copy.returnType, paramsMap);
						if (copy.arguments != null) {
							for (int i = 0; i < copy.arguments.length; i++)
								copy.arguments[i].type = Util.replaceRef(copy.arguments[i].type, paramsMap);
						}
					}
					Method m = new Method(updateMDecl(copy, x, _ast, p), declRef);
					if (!m.method.isStatic()) allMethods.add(m);
				}
				return allMethods;
			} else {
				ArrayList<Method> res = new ArrayList<Method>();
				HashMap<Method, ArrayList<Method>> map = shadow(allMethods);
				Set<Method> keySet = map.keySet();
				for (EclipseNode x : node.down()) {
					if (!(x.get() instanceof MethodDeclaration)) continue;
					MethodDeclaration tempDecl = (MethodDeclaration) x.get(); // Warning: reference, unsafe. // Safe now, I believe.
					Method m = new Method(updateMDecl(tempDecl, x, _ast, p), declRef);
					if (m.method.isStatic()) continue; // ?
					Method key = null;
					for (Method temp : map.keySet()) if (temp.equals(m)) {key = temp; break;}
					if (key == null) res.add(m);
					else if (canOverride(m, map.get(key))) {res.add(m); keySet.remove(key);}
					else return null;
				}
				for (Method m : keySet) {
					if (map.get(m).size() < 1) continue;
					Method mostSpecific = mostSpecific(map.get(m));			
					if (mostSpecific == null) return null;
					res.add(mostSpecific);
				}
				return res;
			}
		}
		/* Shadow methods to get the specific one. */
		private HashMap<Method, ArrayList<Method>> shadow(ArrayList<Method> allMethods) {
			HashMap<Method, ArrayList<Method>> res = new HashMap<Method, ArrayList<Method>>();
			for (Method m : allMethods) {
				boolean add = true;
				for (Method key : res.keySet()) {
					if (key.equals(m)) {
						ArrayList<Method> value = res.get(key);
						ArrayList<Method> newValue = new ArrayList<Method>();
						boolean add2 = true;
						for (Method temp : value) {
							if (subType(temp.origin, comp, m.origin, comp)) {add2 = false; break;}
							if (!subType(m.origin, comp, temp.origin, comp)) newValue.add(temp);
						}
						if (add2) {newValue.add(m); res.put(key, newValue);}
						add = false;
						break;
					}
				}
				ArrayList<Method> singleton = new ArrayList<Method>();
				singleton.add(m);
				if (add) res.put(m, singleton);
			}
			return res;
		}
		/* See if m can override ms. */
		private boolean canOverride(Method m, ArrayList<Method> ms) {
			for (Method temp : ms) if (!subType(m.method.returnType, comp, temp.method.returnType, comp)) return false;
			return true;
		}		
		private Method mostSpecific(ArrayList<Method> ms) {
			if (ms.size() < 1) return null;
			if (ms.size() == 1) return ms.get(0);
			Method[] msArray = ms.toArray(new Method[ms.size()]);
			Method res = msArray[0];
			if (res.method.isDefaultMethod()) return null;
			for (int i = 1; i < msArray.length; i++) {
				Method m = msArray[i];
				if (m.method.isDefaultMethod()) return null;
				if (subType(res.method.returnType, comp, m.method.returnType, comp)) continue;
				if (subType(m.method.returnType, comp, res.method.returnType, comp)) res = m;
				else return null;
			}
			return res;
		}
	}
	static class Printer {
		/* Print TypeReference t as a string. */
		static String toString(TypeReference t) {
			return getRefName(t);
		}
		/* Print MethodDeclaration m as a string. */
		static String toString(MethodDeclaration m) {
			String typeParams = "";
			if (m.typeParameters != null) {
				for (int i = 0; i < m.typeParameters.length; i++) {
					if (typeParams.isEmpty()) typeParams += m.typeParameters[i].toString();
					else typeParams += "," + m.typeParameters[i].toString();
				}
			}
			if (!typeParams.isEmpty()) typeParams = " <" + typeParams + "> ";
			String args = "";
			if (m.arguments != null) {
				for (int i = 0; i < m.arguments.length; i++)
					args += ", " + toString(m.arguments[i].type) + " " + String.valueOf(m.arguments[i].name);
				if (args.length() > 0) args = args.substring(2, args.length());
			}
			String modifier = "";
			if (m.isDefaultMethod()) modifier = "default ";
			if (m.isStatic()) modifier = "static ";
			return modifier + typeParams + toString(m.returnType) + " " + String.valueOf(m.selector) + "(" + args + ");";
		}
	}
	static class Debug {
		/* Print out some information in log.txt, for debugging use. */
		static void printLog(String s, String file) {
			try {
				BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
				bw.write(s + "\n");
				bw.close();
			} catch (IOException e) {}
		}
		/* This method works on Haoyuan's PC. */
		static void printLog(String s) {
			printLog(s, "C:\\Users\\lenovo\\Desktop\\log.txt");
		}
		static void clearLog() {
			try {
				BufferedWriter bw = new BufferedWriter(new FileWriter("C:\\Users\\lenovo\\Desktop\\log.txt", false));
				bw.write("");
				bw.close();
			} catch (IOException e) {}
		}
	}
}

final class Method {
	MethodDeclaration method;
	TypeReference origin;
	Method(MethodDeclaration method, TypeReference origin) { this.method = method; this.origin = origin; }
	boolean equals(Method _m) {
		MethodDeclaration m = _m.method;
		if (!Arrays.equals(m.selector, method.selector)) return false;
		int length1 = 0, length2 = 0;
		if (m.arguments != null) length1 = m.arguments.length;
		if (method.arguments != null) length2 = method.arguments.length;
		if (length1 != length2) return false;
		for (int i = 0; i < length1; i++) {
			TypeReference t1 = copyType(m.arguments[i].type);
			TypeReference t2 = copyType(method.arguments[i].type);
			if (!Util.sameTypeG(t1, t2)) return false;
		}
		return true;
	}
	@Override public String toString() { return Util.Printer.toString(method); }
}

final class Type {
	Method[] methods;
	Type(Method[] ms) {
		methods = new Method[ms.length];
		for (int i = 0; i < ms.length; i++) methods[i] = ms[i]; 
	}
	@Override public String toString() {
		String res = "";
		for (int i = 0; i < methods.length; i++) res += methods[i].toString() + "\n";
		return res;
	}
}
