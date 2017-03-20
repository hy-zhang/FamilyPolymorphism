package lombok.eclipse.handlers.family.util;

import org.eclipse.jdt.internal.compiler.ast.*;
import lombok.eclipse.EclipseNode;

/**
 * @author Haoyuan
 * Auxiliary functions related to locating.
 */
public class Path {
	
	/**
	 * Given a type reference and its context, return the EclipseNode of the corresponding type.
	 * Example: interface A extends B, C {...}
	 * Here B and C are type references, the algorithm below will find the exact type of B and C.
	 * - Generics added.
	 * @param name: type reference
	 * @param here: context where name is used
	 * @return: EclipseNode indicating the TypeDeclaration
	 */
	public static EclipseNode getTypeDecl(String _name, EclipseNode here) {
		String name = General.removeTypeArgs(_name);
		if (here.get() instanceof CompilationUnitDeclaration) return getTypeDeclFromAbsolutePath(name, here);
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
	
	/**
	 * Return the EclipseNode of the exact type of [outside].name.
	 * Example: interface A extends B { interface C extends D {} ... }
	 * Here D is actually A.D, but it is either directly defined inside A's body,
	 * or inherited from A's super types, e.g. B. For the second case we further
	 * check if B.D exists. This method may return null if a conflict exists.
	 * - Generics added.
	 * @param name: type Reference
	 * @param outside: enclosing type
	 * @return: EclipseNode indicating the TypeDeclaration
	 */
	public static EclipseNode getTypeDecl2(String _name, EclipseNode outside) {
		String name = General.removeTypeArgs(_name);
		if (outside.get() instanceof CompilationUnitDeclaration) return getChild(outside, name);
		EclipseNode isChild = getChild(outside, name);
		if (isChild != null) return isChild;
		TypeDeclaration decl = (TypeDeclaration) outside.get();
		EclipseNode res = null;
		String resStr = "";
		if (decl.superInterfaces == null) return null;
		for (TypeReference x : decl.superInterfaces) {
			EclipseNode resX = getTypeDecl2(name, getTypeDecl(x.toString(), outside));
			if (resX == null) continue;
			String resXStr = getAbsolutePathForType(resX);
			if (res == null) { res = resX; resStr = resXStr; }
			else if (!resStr.equals(resXStr)) return null; // conflict. 
		}
		return res;
	}
	
	
	/**
	 * Given the absolute path of a type as a String, and the compilationUnit of the handled file,
	 * return the located EclipseNode in the AST.
	 * - Generics added.
	 * @param name: absolute path
	 * @param compilationUnit: compilation unit of the handled file
	 * @return: EclipseNode corresponding to the TypeDeclaration
	 */
	public static EclipseNode getTypeDeclFromAbsolutePath(String name, EclipseNode compilationUnit) {
		String[] names = General.removeTypeArgs(name).split("\\.");
		EclipseNode res = getTypeDecl2(names[0], compilationUnit);
		for (int i = 1; i < names.length; i++) res = getTypeDecl2(names[i], res);
		return res;
	}
	
	/**
	 * Check if child is an interface directly defined in node.
	 * - Generics added.
	 * @param node: enclosing type
	 * @param child: interface name
	 * @return: the EclipseNode if found, otherwise null
	 */
	public static EclipseNode getChild(EclipseNode node, String _child) {
		String child = General.removeTypeArgs(_child);
		for (EclipseNode x : node.down()) {
			if (!(x.get() instanceof TypeDeclaration)) continue;
			TypeDeclaration y = (TypeDeclaration) x.get();
			if (!General.isInterface(y)) continue;
			if (String.valueOf(y.name).equals(child)) return x;
		}
		return null;
	}
	
	/**
	 * Return the absolute path of an EclipseNode.
	 * - Generics added.
	 * @param node: EclipseNode, can be a type declaration or a method declaration
	 * @return: the absolute path of the type declaration or the enclosing type of the method declaration
	 */
	public static String getAbsolutePathForType(EclipseNode node) {
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
