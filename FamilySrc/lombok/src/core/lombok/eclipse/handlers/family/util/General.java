package lombok.eclipse.handlers.family.util;

import java.util.Arrays;
import java.util.Map;

import static lombok.eclipse.handlers.EclipseHandlerUtil.*;
import static lombok.eclipse.Eclipse.ECLIPSE_DO_NOT_TOUCH_FLAG;

import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.TypeConstants;

import lombok.eclipse.Eclipse;

/**
 * @author Haoyuan
 * Commonly-used auxiliary functions.
 */
public class General {
	
	/**
	 * Check if a TypeDeclaration declares an interface.
	 * @param t: TypeDeclaration
	 * @return: whether it is an interface
	 */
	public static boolean isInterface(TypeDeclaration t) {
		return (t.modifiers & ClassFileConstants.AccInterface) != 0;
	}
	
	/**
	 * Check if a MethodDeclaration declares a field.
	 * Compared to the old version, we further require that it has no type parameters.
	 * @param m: MethodDeclaration
	 * @return: whether it is a field method
	 */
	public static boolean isField(MethodDeclaration m) {
		if (m.isDefaultMethod() || m.isStatic() || isVoidMethod(m)) return false;
		if (m.typeParameters != null && m.typeParameters.length != 0) return false;
		if (m.arguments != null && m.arguments.length != 0) return false;
		if (m.selector.length < 2) return false;
		if (m.selector[0] != '_') return false;
		return true;
	}
	
	/**
	 * Check if a MethodDeclaration is a void method.
	 * @param m: MethodDeclaration
	 * @return: whether the return type is void
	 */
	static boolean isVoidMethod(MethodDeclaration m) {
		if (!(m.returnType instanceof SingleTypeReference)) return false;
		return Arrays.equals(TypeConstants.VOID, ((SingleTypeReference) m.returnType).token);
	}
	
	/**
	 * Remove type arguments from a String.
	 * @param s: String
	 * @return: new String with type arguments removed
	 */
	public static String removeTypeArgs(String s) {
		String str = new String(s);
		while (str.indexOf('<') != -1) {
			int a = str.indexOf('<');
			int b = str.lastIndexOf('>');
			str = str.substring(0, a) + str.substring(b + 1);
		}
		return str;
	}
	
	/**
	 * Get the type arguments in a TypeReference as a TypeReference array.
	 * @param t: TypeReference
	 * @return: array of type arguments
	 */
	public static TypeReference[] getTypeArguments(TypeReference t) {
		if (t instanceof ParameterizedSingleTypeReference) {
			ParameterizedSingleTypeReference t2 = (ParameterizedSingleTypeReference) t;
			TypeReference[] res = new TypeReference[t2.typeArguments.length];
			for (int i = 0; i < res.length; i++) res[i] = copyType(t2.typeArguments[i]);
			return res;
		} else if (t instanceof ParameterizedQualifiedTypeReference) {
			ParameterizedQualifiedTypeReference t2 = (ParameterizedQualifiedTypeReference) t;
			TypeReference[] res = new TypeReference[t2.typeArguments[t2.typeArguments.length - 1].length];
			for (int i = 0; i < res.length; i++) res[i] = copyType(t2.typeArguments[t2.typeArguments.length - 1][i]);
			return res;
		} else return new TypeReference[0];
	}
	
	/**
	 * Get the type parameters of an interface. Bounds are removed.
	 * @param t: TypeDeclaration
	 * @return: type parameters as a String array
	 */
	public static String[] getTypeParameterNames(TypeDeclaration t) {
		if (t.typeParameters != null) {
			String[] res = new String[t.typeParameters.length];
			for (int i = 0; i < res.length; i++) res[i] = new String(t.typeParameters[i].name);
			return res;
		} else return new String[0];
	}
	
	/**
	 * Update a TypeReference with the inside type parameters instantiated.
	 * @param t: TypeReferece
	 * @param map: instantiation of type parameters
	 * @return: updated TypeReference
	 */
	public static TypeReference instantiateTypeReference(TypeReference t, Map<String, TypeReference> map) {
		TypeReference res = copyType(t);
		if (map.isEmpty()) return res;
		if (res instanceof ParameterizedSingleTypeReference) {
			ParameterizedSingleTypeReference res2 = (ParameterizedSingleTypeReference) res;
			for (int i = 0; i < res2.typeArguments.length; i++)
				res2.typeArguments[i] = instantiateTypeReference(res2.typeArguments[i], map);
			return res;
		} else if (res instanceof ParameterizedQualifiedTypeReference) {
			ParameterizedQualifiedTypeReference res2 = (ParameterizedQualifiedTypeReference) res;
			for (int i = 0; i < res2.typeArguments[res2.typeArguments.length - 1].length; i++)
				res2.typeArguments[res2.typeArguments.length - 1][i] = instantiateTypeReference(res2.typeArguments[res2.typeArguments.length - 1][i], map);
			return res;
		} else if (res instanceof SingleTypeReference) {
			SingleTypeReference res2 = (SingleTypeReference) res;
			String name = String.valueOf(res2.token);
			if (map.containsKey(name)) return copyType(map.get(name));
			else return res;
		} else return res;
	}
	
	/**
	 * Creates a new MethodDeclaration.
	 * @param comp: CompilationResult of the injected type
	 * @return: the created MethodDeclaration (injection has not been applied)
	 */
	public static MethodDeclaration newMethod(CompilationResult comp) {
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
	
	/**
	 * Creates a new TypeDeclaraton.
	 * @param name: type name
	 * @param comp: CompilationResult of the injected type
	 * @return: the created TypeDeclration (injection has not been applied)
	 */
	public static TypeDeclaration newType(String name, CompilationResult comp) {
		TypeDeclaration res = new TypeDeclaration(comp);
		res.bits |= Eclipse.ECLIPSE_DO_NOT_TOUCH_FLAG;
		res.modifiers = ClassFileConstants.AccDefault | ClassFileConstants.AccInterface;
		res.name = name.toCharArray();
		return res;
	}
	
}
