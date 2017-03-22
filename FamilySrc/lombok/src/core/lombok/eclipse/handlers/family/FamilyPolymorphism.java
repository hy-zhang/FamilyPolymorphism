package lombok.eclipse.handlers.family;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.internal.compiler.ast.*;

import static lombok.eclipse.handlers.EclipseHandlerUtil.*;

import lombok.eclipse.EclipseNode;
import lombok.eclipse.handlers.family.util.General;
import lombok.eclipse.handlers.family.util.Path;

/**
 * @author Haoyuan
 * First step of @Family processing.
 * Automatically fills in the "extends", namely builds the subtyping
 * relations among family members. Also auto-refine field types by
 * simply copying type reference names.
 * Note: interfaces can be nested in multiple levels, such processing
 * needs to be recursive.
 */
public class FamilyPolymorphism {
	
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
	
	/**
	 * Constructor.
	 * @param node
	 * @param annotationNode
	 * @param p
	 */
	public FamilyPolymorphism(EclipseNode node, EclipseNode annotationNode, Annotation ast, long p) {
		this.node = node;
		this.annotationNode = annotationNode;
		this.ast = ast;
		this.p = p;
		
		boolean autoGenerate = autoGenerate(this.node);
		if (autoGenerate) println(this.node.get().toString());
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
	
	/**
	 * Note 1: If we have "A <: B" in one superInterface, but "A <: B, C" in another superInterface,
	 * should we put them together or throw an error? In current version, we strictly require that the
	 * super types of A should be the same. Users cannot build additional subtyping relations among existing
	 * members, however can add new members.
	 * Note 2: "interface Lit extends FB.Exp ..." is not recommended. We improve the old algorithm, now for each
	 * superInterface, we check the fields, members inside, and check the subtyping among those members. The above
	 * example "interface Lit extends FB.Exp ..." is not treated as a member, but we still apply @Family to it
	 * recursively. But the example:
	 * interface Lit extends FA.Lit, FB.Lit, Exp
	 * should be treated as Lit <: Exp only. We only focus on SingleTypeReference in its superInterfaces.
	 * Note 3: If we have "interface Exp<E extends Exp<E>>" in FA and the same "interface Exp<E extends Exp<E>>"
	 * in FB, it is very complicated to check that the bounds are consistent, instead we delegate this work to
	 * Java compiler. We only check the type parameters are consistent, after removing bounds. For example:
	 * FA: Exp<E>; Lit<F> <: Exp<F>
	 * FB: Exp<T>; Lit<S> <: Exp<S>
	 * Our algorithm should be able to tell that they are consistent. For fields, we check if the signatures of their
	 * types are the same, but notice that families can also have type parameters, for example:
	 * FA: interface FA<E> { List<E> x(); }
	 * FB: interface FB<F> { List<F> x(); }
	 * Our algorithm should be able to tell that they are consistent.
	 * Note 4: If we want to copy a member Exp, but it has been manually defined by user:
	 * 1) interface Exp: but Exp is supposed to have type parameters: throw a warning/error;
	 * 2) interface Exp<...> extends ...: we check the superIntefaces, and add the missing ones. Warning:
	 * if user writes interface FC extends FA, FB { interface Add extends FA.Add, FB.Add, FC.Exp {} }
	 * our algorithm won't treat Exp as a local member, but an additional super type added by user. That means,
	 * we will still generate Exp as a super interface for FC.Add. User is supposed to remove the duplicate.
	 * Note 5: We are considering adding a few new annotations:
	 * 1. @Unfamily: the annotated interface will not be applied with @Family recursively
	 * 2. @Skip: the annotated interface/method is not treated as a member/field.
	 * 3. @Fbound: for example, if Exp is annotated, we will add f-bounds for Exp and its subtypes.
	 * Note 6: A field should not have its own type parameters.
	 * @param child
	 * @param superInterfaces
	 */
	private boolean autoGenerate(EclipseNode child) {
		
		/*
		 * Get the super interfaces of child as EclipseNode[].
		 */
		if (!(child.get() instanceof TypeDeclaration)) return true;
		TypeDeclaration childDecl = (TypeDeclaration) child.get();
		if (!General.isInterface(childDecl)) return true;
		if (childDecl.superInterfaces == null || childDecl.superInterfaces.length == 0) return true;
		EclipseNode[] superInterfaces = new EclipseNode[childDecl.superInterfaces.length];
		for (int i = 0; i < superInterfaces.length; i++) {
			superInterfaces[i] = Path.getTypeDecl(childDecl.superInterfaces[i].toString(), child);
			if (superInterfaces[i] == null) return false;
		}
		
		/*
		 * Get the type arguments of each super interface, for instantiation.
		 */
		Map<String, TypeReference[]> typeArguments = new HashMap<String, TypeReference[]>();
		for (TypeReference t : childDecl.superInterfaces) {
			String absolutePath = Path.getAbsolutePathForType(Path.getTypeDecl(t.toString(), child));
			if (!absolutePath.isEmpty()) {
				TypeReference[] typeArgumentsArray = General.getTypeArguments(t);
				if (typeArgumentsArray.length > 0) typeArguments.put(absolutePath, typeArgumentsArray);
			}
		}
		
		/*
		 * Get the fields and members for code generation later.
		 */
		Map<String, Field> fields = new HashMap<String, Field>();
		Map<String, Member> members = new HashMap<String, Member>();
		
		for (EclipseNode superInterface : superInterfaces) {
			TypeDeclaration superInterfaceDecl = (TypeDeclaration) superInterface.get();
			
			String[] typeParameters = General.getTypeParameterNames(superInterfaceDecl);
			Map<String, TypeReference> instantiation = new HashMap<String, TypeReference>();
			String absolutePath = Path.getAbsolutePathForType(superInterface);
			if (!absolutePath.isEmpty() && typeArguments.containsKey(absolutePath)) {
				TypeReference[] typeArgumentsArray = typeArguments.get(absolutePath);
				if (typeParameters.length == typeArgumentsArray.length) {
					for (int i = 0; i < typeParameters.length; i++)
						instantiation.put(typeParameters[i], typeArgumentsArray[i]);
				}
			}
			
			for (EclipseNode x : superInterface.down()) {
				if (x.get() instanceof TypeDeclaration) {
					TypeDeclaration tDecl = (TypeDeclaration) x.get();
					String absolutePathX = Path.getAbsolutePathForType(x);
					String memberName = String.valueOf(tDecl.name);
					String[] typeParametersX = General.getTypeParameterNames(tDecl);
					Set<String> superMembers = new HashSet<String>();
					Map<String, TypeReference> superMembersTypeReference = new HashMap<String, TypeReference>();
					if (tDecl.superInterfaces != null) {
						for (int i = 0; i < tDecl.superInterfaces.length; i++) {
							TypeReference superInterfaceI = copyType(tDecl.superInterfaces[i]);
							String superInterfaceName = General.removeTypeArgs(superInterfaceI.toString());
							if (superInterfaceName.indexOf('.') != -1) continue;
							superMembers.add(superInterfaceName);
							superMembersTypeReference.put(superInterfaceName, superInterfaceI);
						}
					}
					Member member = new Member(memberName, tDecl.typeParameters, typeParametersX, superMembersTypeReference, superMembers, this.ast);
					if (!members.containsKey(memberName)) {
						member.sources.add(absolutePathX);
						members.put(memberName, member);
					} else if (!members.get(memberName).equals(member)) {
						println("Error: member " + memberName + " has unresolved conflicts. Please check.");
						return false;
					} else {
						Member oldMember = members.get(memberName);
						oldMember.sources.add(absolutePathX);
						members.put(memberName, oldMember);
					}
				} else if (x.get() instanceof MethodDeclaration) {
					MethodDeclaration mDecl = (MethodDeclaration) x.get();
					String fieldName = String.valueOf(mDecl.selector);
					if (General.isField(mDecl)) {
						Field field = new Field(mDecl.returnType, instantiation, fieldName);
						if (!fields.containsKey(fieldName)) fields.put(fieldName, field);
						else if (fields.get(fieldName).hasConflict) continue;
						else if (!fields.get(fieldName).equals(field)) {
							field.hasConflict = true;
							fields.put(fieldName, field);
						}
					}
				}
			}
		}
		
		/*
		 * Auto-generation for type-refined fields.
		 */
		for (String field : fields.keySet()) {
			if (findSameField(child, field)) continue;
			Field fieldObject = fields.get(field);
			if (fieldObject.hasConflict) {
				println("Error: field " + field + " has unresolved conflicts. Please manually refine it.");
				return false;
			}
			copyField(child, fieldObject);
		}
		
		/*
		 * Auto-generation for members.
		 */
		for (String member : members.keySet()) {
			Member memberObject = members.get(member);
			EclipseNode findSameMember = Path.getChild(child, member);
			if (findSameMember != null) {
				TypeDeclaration sameMemberDecl = (TypeDeclaration) findSameMember.get();
				int typeParamsCount = sameMemberDecl.typeParameters == null ? 0 : sameMemberDecl.typeParameters.length;
				if (typeParamsCount != memberObject.typeParameters.length) {
					println("Error: member " + member + " is supposed to have " + memberObject.typeParameters.length + " type parameters.");
					return false;
				}
				Map<String, TypeReference> instantiation = new HashMap<String, TypeReference>();
				if (sameMemberDecl.typeParameters != null) {
					String[] typeParamNames = General.getTypeParameterNames(sameMemberDecl);
					for (int i = 0; i < sameMemberDecl.typeParameters.length; i++)
						instantiation.put(memberObject.typeParameters[i], new SingleTypeReference(typeParamNames[i].toCharArray(), this.p));
				}
				TypeReference[] generateSuperInterfaces = generateSuperInterfaces(memberObject, instantiation);
				Set<String> qualifiedTypeSuperInterfacesDeclaredByUser = new HashSet<String>();
				Set<String> singleTypeSuperInterfacesDeclaredByUser = new HashSet<String>();
				if (sameMemberDecl.superInterfaces != null) {
					for (TypeReference t : sameMemberDecl.superInterfaces) {
						String name = General.removeTypeArgs(t.toString());
						if (name.indexOf(".") == -1) singleTypeSuperInterfacesDeclaredByUser.add(name);
						else qualifiedTypeSuperInterfacesDeclaredByUser.add(Path.getAbsolutePathForType(Path.getTypeDecl(name, findSameMember)));
					}
				}
				List<TypeReference> noDuplicate = new ArrayList<TypeReference>();
				for (TypeReference t : generateSuperInterfaces) {
					String name = General.removeTypeArgs(t.toString());
					if (name.indexOf(".") == 1 && singleTypeSuperInterfacesDeclaredByUser.contains(name)) continue;
					if (name.indexOf(".") != 1 && qualifiedTypeSuperInterfacesDeclaredByUser.contains(name)) continue;
					noDuplicate.add(t);
				}
				sameMemberDecl.superInterfaces = noDuplicate.size() == 0 ? null : noDuplicate.toArray(new TypeReference[noDuplicate.size()]);
			} else {
				TypeDeclaration memberDecl = General.newType(member, childDecl.compilationResult);
				TypeParameter[] generatedTypeParameters = null;
				Map<String, TypeReference> instantiation = new HashMap<String, TypeReference>();
				if (memberObject.typeParameters.length > 0) {
					generatedTypeParameters = copyTypeParams(memberObject.copyOfTypeParams, this.ast);
					for (int i = 0; i < generatedTypeParameters.length; i++) {
						generatedTypeParameters[i].name = ("TEMP" + (i + 1)).toCharArray();
						instantiation.put(memberObject.typeParameters[i], new SingleTypeReference(("TEMP" + (i + 1)).toCharArray(), this.p));
					}
					for (int i = 0; i < generatedTypeParameters.length; i++) {
						if (generatedTypeParameters[i].type != null) {
							TypeReference temp = General.instantiateTypeReference(generatedTypeParameters[i].type, instantiation);
							generatedTypeParameters[i].type = copyType(temp);
						}
						if (generatedTypeParameters[i].bounds != null) {
							for (int j = 0; j < generatedTypeParameters[i].bounds.length; j++) {
								TypeReference temp = General.instantiateTypeReference(generatedTypeParameters[i].bounds[j], instantiation);
								generatedTypeParameters[i].bounds[j] = copyType(temp);
							}
						}
					}
				}
				
				TypeReference[] generateSuperInterfaces = generateSuperInterfaces(memberObject, instantiation);
				memberDecl.typeParameters = generatedTypeParameters;
				memberDecl.superInterfaces = generateSuperInterfaces.length == 0 ? null : generateSuperInterfaces;
				injectType(child, memberDecl);
			}
		}
		
		/*
		 * Recursively apply @Family to all nested interfaces.
		 */
		for (EclipseNode newChild : child.down()) {
			boolean b = autoGenerate(newChild);
			if (!b) return false;
		}
		
		return true;
	}
	
	/**
	 * Check if field is already manually defined by user in child.
	 * @param child: EclipseNode for TypeDeclaration
	 * @param field: String
	 * @return: whether a field with the same name is already defined
	 */
	private boolean findSameField(EclipseNode child, String field) {
		for (EclipseNode x : child.down()) {
			if (!(x.get() instanceof MethodDeclaration)) continue;
			MethodDeclaration m = (MethodDeclaration) x.get();
			if (!String.valueOf(m.selector).equals(field)) continue;
			if (General.isField(m)) return true;
		}
		return false;
	}
	
	/**
	 * Copy a field and inject it to child.
	 * @param child: EclipseNode
	 * @param field: Field
	 */
	private void copyField(EclipseNode child, Field field) {
		MethodDeclaration newMethod = General.newMethod(((TypeDeclaration) child.get()).compilationResult);
		newMethod.selector = field.name.toCharArray();
		newMethod.returnType = copyType(field.type);
		injectMethod(child, newMethod);
	}
	
	/**
	 * Generate the superInterfaces as TypeReference[] for member m and the given bounded type parameters.
	 * @param m: Member
	 * @param typeParameters: bounded type parameters
	 * @return: TypeReference[]
	 */
	private TypeReference[] generateSuperInterfaces(Member m, Map<String, TypeReference> instantiation) {
		TypeReference[] res = new TypeReference[m.superMembers.size() + m.sources.size()];
		int size = 0;
		for (String superMember : m.superMembers) {
			TypeReference superTypeReference = copyType(m.superMembersTypeReference.get(superMember));
			if (!instantiation.isEmpty()) {
				superTypeReference = General.instantiateTypeReference(superTypeReference, instantiation);
			}
			res[size++] = superTypeReference;
		}
		for (int i = 0; i < m.sources.size(); i++) {
			String[] tokenStrings = m.sources.get(i).split("\\.");
			char[][] tokens = new char[tokenStrings.length][];
			for (int k = 0; k < tokens.length; k++) tokens[k] = tokenStrings[k].toCharArray();
			long[] ps = new long[tokens.length];
			for (int k = 0; k < ps.length; k++) ps[k] = this.p;
			if (m.typeParameters.length == 0) {
				res[size + i] = new QualifiedTypeReference(tokens, ps);
			} else {
				TypeReference[][] typeArguments = new TypeReference[tokens.length][];
				for (int k = 0; k < typeArguments.length - 1; k++) typeArguments[k] = null;
				typeArguments[typeArguments.length - 1] = new TypeReference[m.typeParameters.length];
				for (int k = 0; k < m.typeParameters.length; k++)
					typeArguments[typeArguments.length - 1][k] = new SingleTypeReference(("TEMP" + (k + 1)).toCharArray(), this.p);
				res[size + i] = new ParameterizedQualifiedTypeReference(tokens, typeArguments, 0, ps);
			}
		}
		return res;
	}
	
	/**
	 * @author Haoyuan
	 * Internal field type.
	 */
	private static class Field {
		
		/**
		 * TypeReference of the field.
		 */
		TypeReference type;
		
		/**
		 * Name of the field.
		 */
		String name;
		
		/**
		 * This field is set to true if a conflict is found on types of two fields.
		 * If a field has conflict but is manually refined in the annotated type,
		 * we simply skip that field. Otherwise return with an error/warning.
		 */
		boolean hasConflict = false;
		
		/**
		 * Constructor.
		 * @param type: TypeReference before instantiation
		 * @param boundedTypeParameters: instantiation
		 * @param name: field name
		 */
		Field(TypeReference type, Map<String, TypeReference> boundedTypeParameters, String name) {
			this.type = copyType(type);
			if (!boundedTypeParameters.isEmpty())
				this.type = General.instantiateTypeReference(this.type, boundedTypeParameters);
			this.name = new String(name);
		}
		
		/**
		 * This method is used to check if two fields from multiple inheritance has conflicts when using @Family.
		 * Considering it is too complicated to check the subtyping of two TypeReference(s) with generics,
		 * this method only compares the strings of two types after type parameter instantiation. The rest is
		 * delegated to Java compiler. If two fields happen to be different in names but can actually be updated
		 * by @Family (though not recommended), this method will reject this case and ask users to manually update
		 * the fields.
		 * @param f: Field
		 * @return: whether they are equal after instantiation.
		 */
		boolean equals(Field f) {
			if (!this.name.equals(f.name)) return false;
			return this.type.toString().equals(f.type.toString());
		}
		
	}
	
	/**
	 * Internal member type.
	 * @author Haoyuan
	 */
	private static class Member {
		
		/**
		 * Name of the member type.
		 */
		String name;
		
		/**
		 * Copy of type parameters;
		 */
		TypeParameter[] copyOfTypeParams;
		
		/**
		 * Type parameters in the declaration of the member.
		 */
		String[] typeParameters;
		
		/**
		 * TypeReferences of the super members.
		 */
		Map<String, TypeReference> superMembersTypeReference;
		
		/**
		 * Names of the super members.
		 */
		Set<String> superMembers;
		
		/**
		 * Accumulated list of Strings that stores the absolute path
		 * of sources that have the same member name.
		 */
		List<String> sources = new ArrayList<String>();
		
		/**
		 * Constructor.
		 * @param name: member name
		 * @param typeParameters: declared type parameters
		 * @param superMembersTypeReference: TypeReferences bounded by typeParameters
		 * @param superMembers: names of super members
		 */
		Member(String name, TypeParameter[] typeParams, String[] typeParameters,
				Map<String, TypeReference> superMembersTypeReference, Set<String> superMembers, ASTNode ast) {
			this.name = new String(name);
			if (typeParams == null) this.copyOfTypeParams = new TypeParameter[0];
			else this.copyOfTypeParams = copyTypeParams(typeParams, ast);
			this.typeParameters = new String[typeParameters.length];
			for (int i = 0; i < this.typeParameters.length; i++)
				this.typeParameters[i] = typeParameters[i];
			this.superMembersTypeReference = new HashMap<String, TypeReference>();
			for (String key : superMembersTypeReference.keySet()) {
				this.superMembersTypeReference.put(key, copyType(superMembersTypeReference.get(key)));
			}
			this.superMembers = new HashSet<String>();
			this.superMembers.addAll(superMembers);
		}
		
		/**
		 * This method checks if two members are consistent.
		 * It focuses only on the super types which are members, i.e., expressed by SingleTypeReference.
		 * After updating the TypeReference of each super member by instantiation,
		 * it only checks if two TypeReferences have the same String. Otherwise,
		 * a warning is thrown and the processing of @Family exits.
		 * @param m: Member
		 * @return: whether they are equal(consistent)
		 */
		boolean equals(Member m) {
			if (!this.name.equals(m.name)) return false;
			if (this.typeParameters.length != m.typeParameters.length) return false;
			if (!this.superMembers.equals(m.superMembers)) return false;
			Map<String, TypeReference> instantiation = new HashMap<String, TypeReference>();
			for (int i = 0; i < this.typeParameters.length; i++)
				instantiation.put(m.typeParameters[i], new SingleTypeReference(this.typeParameters[i].toCharArray(), 0));
			for (int i = 0; i < this.typeParameters.length; i++) {
				TypeReference t1 = this.copyOfTypeParams[i].type;
				TypeReference t2 = m.copyOfTypeParams[i].type;
				if (t1 == null && t2 != null) return false;
				if (t1 != null && t2 == null) return false;
				if (t1 != null && t2 != null) {
					TypeReference t3 = General.instantiateTypeReference(t2, instantiation);
					if (!t1.toString().equals(t3.toString())) return false;
				}
				TypeReference[] t1s = this.copyOfTypeParams[i].bounds;
				TypeReference[] t2s = m.copyOfTypeParams[i].bounds;
				if (t1s == null && t2s != null) return false;
				if (t1s != null && t2s == null) return false;
				if (t1s != null && t2s != null) {
					if (t1s.length != t2s.length) return false;
					for (int k = 0; k < t1s.length; k++) {
						TypeReference t3 = General.instantiateTypeReference(t2s[k], instantiation);
						if (!t1s[k].toString().equals(t3.toString())) return false;
					}
				}
			}
			for (String key : this.superMembersTypeReference.keySet()) {
				TypeReference t1 = this.superMembersTypeReference.get(key);
				TypeReference t2 = copyType(m.superMembersTypeReference.get(key));
				if (!instantiation.isEmpty()) t2 = General.instantiateTypeReference(t2, instantiation);
				if (!t1.toString().equals(t2.toString())) return false;
			}
			return true;
		}
		
	}
	
}
