package lombok.eclipse.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static lombok.eclipse.handlers.EclipseHandlerUtil.*;

import org.eclipse.jdt.internal.compiler.ast.*;
import org.mangosdk.spi.ProviderFor;

import lombok.Obj;
import lombok.core.AnnotationValues;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseNode;

@ProviderFor(EclipseAnnotationHandler.class)
public class HandleObj extends EclipseAnnotationHandler<Obj> {
	
	Annotation ast;
	EclipseNode annotationNode;
	EclipseNode me;
	TypeDeclaration meDecl;
	int pS, pE;
	long p;
	
	String infiniteCheck = "";
	String simpleCheck = "";

	// interface A { interface B { interface C extends A {}}}: forbidden.
	private boolean checkInfinite(EclipseNode node, ArrayList<String> outs) {
		if (!(node.get() instanceof TypeDeclaration)) return false;
		TypeDeclaration nDecl = (TypeDeclaration) node.get();
		if (nDecl.superInterfaces != null) {
			for (TypeReference t : nDecl.superInterfaces) {
				EclipseNode tDecl = Util.Path.getTypeDecl(Util.Printer.toString(t), node);
				if (tDecl != null && outs.contains(Util.Path.getAbsolutePathForType(tDecl))) {
					infiniteCheck = String.valueOf(nDecl.name);
					return true;
				}
			}
		}
		ArrayList<String> newOuts = new ArrayList<String>();
		newOuts.addAll(outs);
		if (outs.size() > 0) newOuts.add(outs.get(outs.size() - 1) + "." + String.valueOf(nDecl.name));
		else newOuts.add(String.valueOf(nDecl.name));
		if (node.down() != null) {
			for (EclipseNode child : node.down())
				if (checkInfinite(child, newOuts)) return true;
		}
		return false;
	}
	
	
	// interface A extends A: prohibited.
	private boolean simpleCheck(EclipseNode node) {
		if (!(node.get() instanceof TypeDeclaration)) return false;
		TypeDeclaration nDecl = (TypeDeclaration) node.get();
		if (nDecl.superInterfaces != null) {
			for (TypeReference t : nDecl.superInterfaces)
				if (Util.Printer.toString(t).equals(String.valueOf(nDecl.name))) {
					simpleCheck = String.valueOf(nDecl.name);
					return true;
				}
		}
		if (node.down() != null) {
			for (EclipseNode child : node.down())
				if (simpleCheck(child)) return true;
		}
		return false;
	}
	
	@Override public void handle(AnnotationValues<Obj> _annotation, Annotation _ast, EclipseNode _annotationNode) {
	
		ast = _ast;
		annotationNode = _annotationNode;
		me = annotationNode.up();
		pS = ast.sourceStart;
		pE = ast.sourceEnd;
		p = (long)pS << 32 | pE;
		meDecl = (TypeDeclaration) me.get();
		
//		Util.Debug.clearLog();
//		Util.Debug.printLog("---Before---\n");
//		Util.Debug.printLog(meDecl.toString());
		
		String mePath = Util.Path.getAbsolutePathForType(me);
		int indexMePath = mePath.lastIndexOf('.');
		ArrayList<String> tempList = new ArrayList<String>();
		if (indexMePath != -1) tempList.add(mePath.substring(0, indexMePath));
		if (checkInfinite(me, tempList)) {
			throwError("Error: Member type " + infiniteCheck + " extends its enclosing type.");
			return;
		}
		if (simpleCheck(me)) {
			throwError("Error: Type " + simpleCheck + " extends itself.");
			return;
		}
		
		if (meDecl.superInterfaces != null) new MultipleInheritance(me);
		
		generateOf(me);
		
		for (EclipseNode x : me.down()) {
			if (!(x.get() instanceof TypeDeclaration)) continue;
			generateOf(x);
		}
		
//		Util.Debug.printLog("\n---After---\n");
//		Util.Debug.printLog(meDecl.toString());
		
	}
	
	public void throwError(String s) {
		annotationNode.addError(s, pS, pS);
	}
	
	private void generateOf(EclipseNode type) {
		new EclipseObjHandlerUtil(ast, annotationNode, type);
	}
	
	class MultipleInheritance {
		EclipseNode node;
		EclipseNode[] upNodes;
		TypeDeclaration nodeDecl;
		MultipleInheritance(EclipseNode _node) {			
			node = _node;
			if (!(node.get() instanceof TypeDeclaration)) return;
			nodeDecl = (TypeDeclaration) node.get();
			if (!Util.isInterface(nodeDecl)) return;
			if (nodeDecl.superInterfaces == null || nodeDecl.superInterfaces.length == 0) return;
			String[] nameParents = new String[nodeDecl.superInterfaces.length];
			upNodes = new EclipseNode[nodeDecl.superInterfaces.length];
			for (int i = 0; i < upNodes.length; i++) {
				nameParents[i] = Util.getRefName(nodeDecl.superInterfaces[i]);
				upNodes[i] = Util.Path.getTypeDecl(nameParents[i], node);
				if (upNodes[i] == null) return;
			}
			start(node, upNodes, nameParents);
		}
		// after this start() method, should check that child is well-formed (mBody is not enough, needs interface typing).
		void start(EclipseNode child, EclipseNode[] parents, String[] nameParents) {
			HashSet<String> fields = new HashSet<String>();
			HashSet<String> members = new HashSet<String>();
			HashMap<String, HashSet<String>> memberSubtyping = new HashMap<String, HashSet<String>>();
			HashMap<String, TypeParameter[]> typeParamsMap = new HashMap<String, TypeParameter[]>();
			// Critical (FOR GENERICS): not checking (in user code) if members from those parents have same subtyping relations.
			for (EclipseNode parent : parents) {
				for (EclipseNode x : parent.down()) {
					if (x.get() instanceof TypeDeclaration) {
						members.add(String.valueOf(((TypeDeclaration) x.get()).name));
						typeParamsMap.put(String.valueOf(((TypeDeclaration) x.get()).name), ((TypeDeclaration) x.get()).typeParameters);
					} else if (x.get() instanceof MethodDeclaration) {
						MethodDeclaration xDecl = (MethodDeclaration) x.get();
						if (Util.isField(xDecl)) fields.add(String.valueOf(xDecl.selector));
					}
				}
			}
			for (String field : fields)
				if (!findSameField(child, field)) copyField(child, parents, field);
			for (String member : members) {
				EclipseNode sameMember = findSameMember(child, member);
				TypeDeclaration sameMemberDecl = null;
				if (sameMember == null) {
					sameMemberDecl = Util.newType(member, ((TypeDeclaration) child.get()).compilationResult);
					sameMemberDecl.typeParameters = copyTypeParams(typeParamsMap.get(member), ast);
					sameMember = injectType(child, sameMemberDecl);
				} else sameMemberDecl = (TypeDeclaration) sameMember.get();
				if (sameMemberDecl.typeParameters != null) continue; // Critical: For generic members, no code generation in preprocess, no further recursion.
				ArrayList<TypeReference> supers = new ArrayList<TypeReference>();
				ArrayList<EclipseNode> newParents = new ArrayList<EclipseNode>();
				ArrayList<String> newNameParents = new ArrayList<String>();
				EclipseNode[] findSameMembers = new EclipseNode[parents.length];
				for (int i = 0; i < parents.length; i++)
					findSameMembers[i] = findSameMember(parents[i], member);
				if (sameMemberDecl.superInterfaces != null) {
					for (int i = 0; i < sameMemberDecl.superInterfaces.length; i++)
						supers.add(copyType(sameMemberDecl.superInterfaces[i]));
				} else {
					HashSet<String> oldSupers = new HashSet<String>();
					boolean init = false;
					for (int i = 0; i < parents.length; i++) {
						if (findSameMembers[i] == null) continue;
						TypeReference[] xs = ((TypeDeclaration) findSameMembers[i].get()).superInterfaces;
						HashSet<String> oldSupers2 = new HashSet<String>();
						if (xs != null) {
							for (TypeReference x : xs)
								if (x instanceof SingleTypeReference) oldSupers2.add(Util.getRefName(x));
								// Otherwise? Doesn't matter.
						}
						if (!init) { oldSupers.addAll(oldSupers2); init = true; }
						else if (!oldSupers.equals(oldSupers2)) {
							String xName = String.valueOf(((TypeDeclaration) findSameMembers[i].get()).name);
							String yName = String.valueOf(((TypeDeclaration) parents[i].get()).name);
							throwError("Error: " + xName + " from " + yName + " has different supertypes.");
						}
					}
					for (String s : oldSupers) supers.add(Util.getNameRef(s, p));
				}
				int oldSize = supers.size();
				for (int i = 0; i < parents.length; i++) {
					if (findSameMembers[i] != null) {
						String newNameParent = nameParents[i] + "." + member;
						supers.add(Util.getNameRef(newNameParent, p));
						newParents.add(findSameMembers[i]);
						newNameParents.add(newNameParent);
					}
				}
				// Avoid duplication.
				ArrayList<TypeReference> supersNoDup = new ArrayList<TypeReference>();
				for (int i = oldSize; i < supers.size(); i++) supersNoDup.add(supers.get(i));
				for (int i = 0; i < oldSize; i++) {
					boolean dup = false;
					for (int j = oldSize; j < supers.size(); j++)
						if (Util.sameType(supers.get(i), sameMember, supers.get(j), sameMember)) {dup = true; break;}
					if (!dup) supersNoDup.add(supers.get(i));
				}
				sameMemberDecl.superInterfaces = supers.toArray(new TypeReference[supers.size()]);
				int newSize = newParents.size();
				start(sameMember, newParents.toArray(new EclipseNode[newSize]), newNameParents.toArray(new String[newSize]));
			}
		}
		boolean findSameField(EclipseNode child, String field) {
			for (EclipseNode x : child.down()) {
				if (!(x.get() instanceof MethodDeclaration)) continue;
				MethodDeclaration m = (MethodDeclaration) x.get();
				if (!field.equals(String.valueOf(m.selector))) continue;
				if (m.arguments == null || m.arguments.length == 0) return true;
			}
			return false;
		}
		void copyField(EclipseNode child, EclipseNode[] parents, String field) { // Multiple inheritance needs support for generics.
			TypeReference fieldType = null;
			

			TypeDeclaration childDecl = (TypeDeclaration) child.get();
			for (int i = 0; i < parents.length; i++) {
				EclipseNode parent = parents[i];
				TypeReference[] tArgs = null;
				if (childDecl.superInterfaces[i] instanceof ParameterizedSingleTypeReference) {
					tArgs = ((ParameterizedSingleTypeReference) childDecl.superInterfaces[i]).typeArguments;
				} else if (childDecl.superInterfaces[i] instanceof ParameterizedQualifiedTypeReference) {
					int len = ((ParameterizedQualifiedTypeReference) childDecl.superInterfaces[i]).typeArguments.length;
					tArgs = ((ParameterizedQualifiedTypeReference) childDecl.superInterfaces[i]).typeArguments[len - 1];
				}
				// Haoyuan. First attempt. Need more modification to let MultipInheritance support generics.
				HashMap<String, TypeReference> map = new HashMap<String, TypeReference>();
				TypeDeclaration parentDecl = (TypeDeclaration) parent.get();
				if (parentDecl.typeParameters != null) {
					for (int k = 0; k < parentDecl.typeParameters.length; k++)
						map.put(String.valueOf(parentDecl.typeParameters[k].name), copyType(tArgs[k]));
				}
				
				
				for (EclipseNode x : parent.down()) {
					if (!(x.get() instanceof MethodDeclaration)) continue;
					MethodDeclaration xDecl = (MethodDeclaration) x.get();
					if (!Util.isField(xDecl) || !field.equals(String.valueOf(xDecl.selector))) continue;
					if (fieldType == null) fieldType = Util.replaceRef(xDecl.returnType, map);
					else if (!fieldType.toString().equals(Util.replaceRef(xDecl.returnType, map).toString())) return; // throwError. toString() correct?
				}
			}
			MethodDeclaration res = Util.newMethod(((TypeDeclaration) child.get()).compilationResult);
			res.selector = field.toCharArray();
			res.returnType = copyType(fieldType);
			injectMethod(child, res);
		}
		EclipseNode findSameMember(EclipseNode child, String member) {
			for (EclipseNode x : child.down()) {
				if (!(x.get() instanceof TypeDeclaration)) continue;
				TypeDeclaration t = (TypeDeclaration) x.get();
				if (String.valueOf(t.name).equals(member)) return x;
			}
			return null; 
		}
	}
	
}
