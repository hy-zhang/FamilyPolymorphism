package lombok.eclipse.handlers;

import static lombok.eclipse.handlers.EclipseHandlerUtil.*;

import java.util.*;

import lombok.eclipse.*;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.*;

public class EclipseObjHandlerUtil {
	
	/**
	 * Just some constants used for Lombok.
	 */
	int pS, pE;
	long p;
	ASTNode _ast;
	Annotation _src;
	Util.MBody mbody;
	
	/**
	 * The interface that EclipseObjHandlerUtil handles.
	 * me:		initialized in the constructor, EclipseNode of the interface declaration
	 * meDecl:	the interface declaration
	 * meAnno:	EclipseNode of an annotation, used for error reporting
	 * meType:	type reference to this interface
	 * compilationUnit:	compilation unit EclipseNode, top node in the compilation of a file
	 * errorMsg:	a string storing error messages for debugging
	 */
	EclipseNode me;
	TypeDeclaration meDecl;
	EclipseNode meAnno;
	TypeReference meType, meTypeFull;
	EclipseNode compilationUnit;
	String errorMsg = "";
	int annoSrc;
	
	/**
	 * Some member fields storing information for generating of() methods.
	 * ofArgs:		list of of() arguments.
	 * ofFields:	fields of the anonymous class inside of() method
	 * ofMethods:	methods of the anonymous class inside of() method
	 * fieldNames: 	names of the fields
	 * fieldsMap:	a mapping from field name to its type
	 */
	ArrayList<MethodDeclaration> ofMethods;
	ArrayList<Argument> ofArgs;
	ArrayList<FieldDeclaration> ofFields;
	String[] fieldNames;
	HashMap<String, TypeReference> fieldsMap;
	
	public EclipseObjHandlerUtil(Annotation ast, EclipseNode annotationNode, EclipseNode node) {
		pS = ast.sourceStart;
		pE = ast.sourceEnd;
		p = (long)pS << 32 | pE;
		_ast = annotationNode.get();
		_src = ast;		
		meAnno = annotationNode;
		me = node;
		annoSrc = ast.sourceStart;
		if (!(me.get() instanceof TypeDeclaration)) {
			throwError("Error: Annotated node is not TypeDeclaration.");
			return;
		}
		meDecl = (TypeDeclaration) me.get();
		meType = Util.getNameRef(String.valueOf(meDecl.name), p);
		meTypeFull = Util.getNameRef(Util.Path.getAbsolutePathForType(me), p);
		
		if (meDecl.typeParameters != null) {
			TypeReference[] refs = new TypeReference[meDecl.typeParameters.length];
			for (int i = 0; i < refs.length; i++) refs[i] = new SingleTypeReference(meDecl.typeParameters[i].name, p);
			meType = new ParameterizedSingleTypeReference(meDecl.name, refs, 0, p);
			if (meTypeFull instanceof SingleTypeReference) {
				meTypeFull = new ParameterizedSingleTypeReference(((SingleTypeReference) meTypeFull).token, refs, 0, p);
			} else {
				char[][] tokens = ((QualifiedTypeReference) meTypeFull).tokens;
				TypeReference[][] types = new TypeReference[tokens.length][];
				for (int i = 0; i < types.length; i++) types[i] = null;
				types[types.length - 1] = refs;
				long[] ps = new long[tokens.length];
				for (int i = 0; i < ps.length; i++) ps[i] = p;
				meTypeFull = new ParameterizedQualifiedTypeReference(tokens, types, 0, ps);
			}
		}
		
		errorMsg = "";
		compilationUnit = me;
		while (compilationUnit.up() != null) compilationUnit = compilationUnit.up();
		mbody = new Util.MBody(p, compilationUnit, _ast);
		
		if (checkValid()) genOfMethod(meDecl.typeParameters);
//		if (errorMsg.length() > 0) meAnno.addError(errorMsg);
	}
	
	private boolean checkValid() {
		if (!Util.isInterface(meDecl)) {
			throwError("Error: @Family only supported on interface.");
			return false;
		}
		fieldsMap = new HashMap<String, TypeReference>();
		HashMap<Method, Boolean> unresolved = new HashMap<Method, Boolean>();
		ofMethods = new ArrayList<MethodDeclaration>();
		ofArgs = new ArrayList<Argument>();
		ofFields = new ArrayList<FieldDeclaration>();
		Util.MBody mbody = new Util.MBody(p, compilationUnit, _ast);
		Type thisType = mbody.mBody(me);
		if (thisType == null) {
			throwError("Error: mBody undefined for " + String.valueOf(meDecl.name));
			return false;
		}
		for (Method m : thisType.methods) {
			if (Util.isField(m.method)) fieldsMap.put(String.valueOf(m.method.selector).substring(1), copyType(m.method.returnType));
			else if (Util.isAbstractMethod(m.method)) unresolved.put(m, true);
		}
		fieldNames = fieldsMap.keySet().toArray(new String[fieldsMap.keySet().size()]);
		Arrays.sort(fieldNames);
		// non-trivial.
//		if (ofAlreadyExists()) {
//			throwError("In checkValid(): of method already defined");
//			return false;
//		}
		for (int i = 0; i < fieldNames.length; i++) {
			String name = fieldNames[i];
			TypeReference type = fieldsMap.get(name);
			ofArgs.add(new Argument(("_" + name).toCharArray(), p, copyType(type), Modifier.NONE));
			ofMethods.add(genGetter(name.toCharArray(), copyType(type)));
			ofFields.add(genField(name.toCharArray(), copyType(type)));
		}
		for (Method m : unresolved.keySet()) {
			if (!unresolved.get(m)) continue;
			String name = String.valueOf(m.method.selector);
			TypeReference type = m.method.returnType;
			Argument[] args = m.method.arguments;
						
			if (meDecl.typeParameters == null && name.equals("with") && args != null && args.length == 1 && subTypeG(meTypeFull, type)) { // with() method doesn't support generics.
				MethodDeclaration generalWith = genGeneralWith(copyType(args[0].type));
				if (generalWith == null) {
					throwError("In checkValid(): type of with method not applicable");
					return false;
				} else {
					if (!Util.sameTypeG(meTypeFull, type)) genAbstractGeneralWith(copyType(args[0].type));
					ofMethods.add(generalWith);
					unresolved.put(m, false);
					continue;
				}
			}
			if (name.charAt(0) == '_' && fieldsMap.containsKey(name.substring(1)) && args != null && args.length == 1 && Util.sameTypeG(args[0].type, fieldsMap.get(name.substring(1)))) {
				if (Util.isVoidMethod(m.method)) {
					ofMethods.add(genSetter(name.substring(1).toCharArray(), copyType(args[0].type), false));
					unresolved.put(m, false);
					continue;
				}
				else if (subTypeG(meTypeFull, type)) {
					if (!Util.sameTypeG(meTypeFull, type)) genAbstractSetter(name.substring(1).toCharArray(), copyType(args[0].type));
					ofMethods.add(genSetter(name.substring(1).toCharArray(), copyType(args[0].type), true));
					unresolved.put(m, false);
					continue;
				}
			}
			String name_substr = "";
			if (name.length() > 5 && name.substring(0, 5).equals("with_") && Character.isLowerCase(name.charAt(5))) name_substr = Util.toLowerCase(name.substring(5));
			if (name_substr.length() > 0 && fieldsMap.containsKey(name_substr) && args != null && args.length == 1 && Util.sameTypeG(args[0].type, fieldsMap.get(name_substr)) && subTypeG(meTypeFull, type)) {
				if (!Util.sameTypeG(meTypeFull, type)) genAbstractWith(name_substr.toCharArray(), copyType(args[0].type));
				ofMethods.add(genWith(name_substr.toCharArray(), copyType(args[0].type)));
				unresolved.put(m, false);
				continue;
			}
			throwError("Unresolved method: " + m.toString());
			return false;
		}
		return true;
	}
	
	private FieldDeclaration genField(char[] name, TypeReference type) {
		FieldDeclaration f = new FieldDeclaration(name, pS, pE);
		f.bits |= Eclipse.ECLIPSE_DO_NOT_TOUCH_FLAG;
		f.modifiers = ClassFileConstants.AccDefault;
		f.type = copyType(type);
		f.initialization = new SingleNameReference(("_" + String.valueOf(name)).toCharArray(), p);
		return f;
	}
	
	private MethodDeclaration genGetter(char[] name, TypeReference type) {
		MethodDeclaration mGetter = newMethod();
		mGetter.modifiers = ClassFileConstants.AccPublic;
		mGetter.returnType = copyType(type);
		mGetter.selector = ("_" + String.valueOf(name)).toCharArray();
		ReturnStatement returnG = new ReturnStatement(new SingleNameReference(name, p), pS, pE);
		mGetter.statements = new Statement[] { returnG };
		return mGetter;
	}
	
	private MethodDeclaration genSetter(char[] name, TypeReference type, boolean fluent) {
		MethodDeclaration mSetter = newMethod();
		Argument arg = new Argument("val".toCharArray(), p, copyType(type), Modifier.NONE);
		mSetter.modifiers = ClassFileConstants.AccPublic;
		mSetter.selector = ("_" + String.valueOf(name)).toCharArray();
		mSetter.arguments = new Argument[]{ arg };
		Assignment assignS = new Assignment(new SingleNameReference(name, p), new SingleNameReference("val".toCharArray(), p), (int)p);
		assignS.sourceStart = pS; assignS.sourceEnd = assignS.statementEnd = pE;
		ReturnStatement returnS = new ReturnStatement(new ThisReference(pS, pE), pS, pE);
		if (fluent) {
			mSetter.statements = new Statement[] { assignS, returnS };
			mSetter.returnType = copyType(meType);
		} else {
			mSetter.statements = new Statement[] { assignS };
			mSetter.returnType = new SingleTypeReference(TypeBinding.VOID.simpleName, p);
		}
		return mSetter;
	}
	
	private void genAbstractSetter(char[] name, TypeReference type) {
		Argument arg = new Argument("val".toCharArray(), p, copyType(type), Modifier.NONE);
		MethodDeclaration mSetter = newMethod();
		mSetter.returnType = copyType(meType);
		mSetter.selector = ("_" + String.valueOf(name)).toCharArray();
		mSetter.arguments = new Argument[]{ arg };
		injectMethod(me, mSetter);
	}
	
	private MethodDeclaration genWith(char[] name, TypeReference type) {
		MethodDeclaration mWith = newMethod();
		Argument arg = new Argument("val".toCharArray(), p, copyType(type), Modifier.NONE);
		mWith.modifiers = ClassFileConstants.AccPublic;
		mWith.returnType = copyType(meType);
		mWith.selector = ("with_" + String.valueOf(name)).toCharArray();
		mWith.arguments = new Argument[]{ arg };
		MessageSend invokeOf = new MessageSend();
		invokeOf.receiver = new SingleNameReference(meDecl.name, p);
		invokeOf.selector = "of".toCharArray();
		invokeOf.arguments = new Expression[fieldNames.length];
		for (int i = 0; i < fieldNames.length; i++) {
			if (fieldNames[i].equals(String.valueOf(name))) invokeOf.arguments[i] = new SingleNameReference("val".toCharArray(), p);
			else invokeOf.arguments[i] = new SingleNameReference(fieldNames[i].toCharArray(), p);
		}
		ReturnStatement returnW = new ReturnStatement(invokeOf, pS, pE);
		mWith.statements = new Statement[] { returnW };
		return mWith;
	}
	
	private void genAbstractWith(char[] name, TypeReference type) {
		Argument arg = new Argument("val".toCharArray(), p, copyType(type), Modifier.NONE);
		MethodDeclaration mWith = newMethod();
		mWith.returnType = copyType(meType);
		mWith.selector = ("with_" + String.valueOf(name)).toCharArray();
		mWith.arguments = new Argument[]{ arg };
		injectMethod(me, mWith);
	}
	
	private MethodDeclaration genGeneralWith(TypeReference type) {
		Type t = mBody(Util.Path.getTypeDeclFromAbsolutePath(Util.getRefName(type), compilationUnit)); // Please check: it's always absolute path.
		if (t == null) return null;
		HashMap<String, TypeReference> fieldsFromType = new HashMap<String, TypeReference>();
		for (Method m : t.methods)
			if (Util.isField(m.method) && Util.sameTypeG(m.method.returnType, fieldsMap.get(String.valueOf(m.method.selector))))
				fieldsFromType.put(String.valueOf(m.method.selector), copyType(m.method.returnType));
		MethodDeclaration mWith = newMethod();
		Argument arg = new Argument("val".toCharArray(), p, copyType(type), Modifier.NONE);
		mWith.modifiers = ClassFileConstants.AccPublic;
		mWith.returnType = copyType(meType);
		mWith.selector = "with".toCharArray();
		mWith.arguments = new Argument[]{ arg };
		MessageSend invokeOf = new MessageSend();
		invokeOf.receiver = new SingleNameReference(meDecl.name, p);
		invokeOf.selector = "of".toCharArray();
		invokeOf.arguments = new Expression[fieldNames.length];
		for (int i = 0; i < fieldNames.length; i++) {
			MessageSend tempM = new MessageSend();
			tempM.receiver = new ThisReference(pS, pE);
			if (fieldsFromType.containsKey(fieldNames[i])) tempM.receiver = new SingleNameReference("val".toCharArray(), p);
			tempM.selector = fieldNames[i].toCharArray();
			tempM.arguments = null;
			invokeOf.arguments[i] = tempM;
		}		
		InstanceOfExpression instanceOfExp = new InstanceOfExpression(new SingleNameReference("val".toCharArray(), p), copyType(meType));
		CastExpression castExp = makeCastExpression(new SingleNameReference("val".toCharArray(), p), copyType(meType), _ast);
		ReturnStatement returnCast = new ReturnStatement(castExp, pS, pE);
		IfStatement ifStmt = new IfStatement(instanceOfExp, returnCast, pS, pE);
		ReturnStatement returnW = new ReturnStatement(invokeOf, pS, pE);
		mWith.statements = new Statement[] { ifStmt, returnW };
		return mWith;
	}
	
	private void genAbstractGeneralWith(TypeReference type) {
		Argument arg = new Argument("val".toCharArray(), p, copyType(type), Modifier.NONE);
		MethodDeclaration mWith = newMethod();
		mWith.returnType = copyType(meType);
		mWith.selector = "with".toCharArray();
		mWith.arguments = new Argument[]{ arg };
		injectMethod(me, mWith);
	}
	
	private void genOfMethod(TypeParameter[] typeParams) {
		MethodDeclaration of = newMethod();
		of.modifiers = ClassFileConstants.AccStatic;
		of.typeParameters = copyTypeParams(typeParams, _ast);
		if (typeParams == null) of.returnType = copyType(meType);
		else {
			TypeReference[] refs = new TypeReference[typeParams.length];
			for (int i = 0; i < refs.length; i++) refs[i] = new SingleTypeReference(typeParams[i].name, p);
			ParameterizedSingleTypeReference pstr = new ParameterizedSingleTypeReference(meDecl.name, refs, 0, p);
			of.returnType = pstr;
		}
		of.selector = "of".toCharArray();
		of.arguments = ofArgs.size() == 0 ? null : ofArgs.toArray(new Argument[ofArgs.size()]);		
		TypeDeclaration anonymous = new TypeDeclaration(meDecl.compilationResult);
		anonymous.bits |= (ASTNode.IsAnonymousType | ASTNode.IsLocalType);
		anonymous.name = CharOperation.NO_CHAR;
		anonymous.typeParameters = null;
		anonymous.fields = ofFields.size() == 0 ? null : ofFields.toArray(new FieldDeclaration[ofFields.size()]);
		anonymous.methods = ofMethods.size() == 0 ? null : ofMethods.toArray(new MethodDeclaration[ofMethods.size()]);		
		QualifiedAllocationExpression alloc = new QualifiedAllocationExpression(anonymous);
		alloc.type = copyType(of.returnType);
		ReturnStatement returnStmt = new ReturnStatement(alloc, pS, pE);
		of.statements = new Statement[] { returnStmt };	
		injectMethod(me, of);
	}
	
	/* Below are auxiliary methods. */
	private void throwError(String s) {
		meAnno.addError(s, pS, pS);
	}
	
	private MethodDeclaration newMethod() {
		return Util.newMethod(meDecl.compilationResult);
	}
	
	private Type mBody(EclipseNode node) {
		Util.MBody mbody = new Util.MBody(p, compilationUnit, _ast);
		return mbody.mBody(node);
	}
	
	private boolean subTypeG(TypeReference t1, TypeReference t2) {
		return Util.subType(t1, compilationUnit, t2, compilationUnit);
	}
	
}



