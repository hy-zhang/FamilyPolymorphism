package lombok.eclipse.handlers.family.util;

import java.util.HashMap;
import java.util.Map;

import static lombok.eclipse.handlers.EclipseHandlerUtil.*;

import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;

import lombok.eclipse.EclipseNode;

/**
 * @author Haoyuan
 * Auxiliary functions related to subtyping.
 */
public class Subtype {

	/**
	 * Check if two TypeReferences are equal.
	 * @param t1: TypeReference
	 * @param here1: context of t1
	 * @param t2: TypeReference
	 * @param here2: context of t2
	 * @return: whether they are equal
	 */
	public static boolean sameType(TypeReference t1, EclipseNode here1, TypeReference t2, EclipseNode here2) {
		EclipseNode decl1 = Path.getTypeDecl(t1.toString(), here1);
		EclipseNode decl2 = Path.getTypeDecl(t2.toString(), here2);
		if (decl1 == null && decl2 == null) return t1.toString().equals(t2.toString());
		if (decl1 == null || decl2 == null) return false;
		String path1 = Path.getAbsolutePathForType(decl1);
		String path2 = Path.getAbsolutePathForType(decl2);
		if (!path1.equals(path2)) return false;
		TypeReference[] typeArgs1 = General.getTypeArguments(t1);
		TypeReference[] typeArgs2 = General.getTypeArguments(t2);
		if (typeArgs1.length != typeArgs2.length) return false;
		for (int i = 0; i < typeArgs1.length; i++) {
			if (!sameType(typeArgs1[i], here1, typeArgs2[i], here2)) return false;
		}
		return true;
	}
	
	/**
	 * Check if t1[here1] <: t2[here2].
	 * @param t1: TypeReference
	 * @param here1: context of t1
	 * @param t2: TypeReference
	 * @param here2: context of t2
	 * @return: whether t1[here1] <: t2[here2]
	 */
	public static boolean subType(TypeReference t1, EclipseNode here1, TypeReference t2, EclipseNode here2) {
		if (sameType(t1, here1, t2, here2)) return true;
		EclipseNode type1 = Path.getTypeDecl(t1.toString(), here1);
		if (type1 == null) return false;
		if (!(type1.get() instanceof TypeDeclaration)) return false;
		TypeDeclaration decl1 = (TypeDeclaration) type1.get();
		if (decl1.superInterfaces != null) {
			Map<String, TypeReference> instantiation = new HashMap<String, TypeReference>();
			if (decl1.typeParameters != null) {
				TypeReference[] typeArguments = General.getTypeArguments(t1);
				String[] typeParamNames = General.getTypeParameterNames(decl1);
				for (int i = 0; i < typeParamNames.length; i++)
					instantiation.put(typeParamNames[i], copyType(typeArguments[i]));
			}
			for (TypeReference superInterface : decl1.superInterfaces) {
				TypeReference t1New = copyType(superInterface);
				if (!instantiation.isEmpty()) t1New = General.instantiateTypeReference(t1New, instantiation);
				if (subType(t1New, type1, t2, here2)) return true;
			}
		}
		return false;
	}
	
}
