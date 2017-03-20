package lombok.eclipse.handlers;

import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.mangosdk.spi.ProviderFor;

import lombok.Family;
import lombok.core.AnnotationValues;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseNode;
import lombok.eclipse.handlers.family.FamilyPolymorphism;
import lombok.eclipse.handlers.family.util.Debug;
import lombok.eclipse.handlers.family.util.General;
import lombok.eclipse.handlers.family.util.Path;

import java.util.ArrayList;

import org.eclipse.jdt.internal.compiler.ast.*;

/**
 * @author Haoyuan
 * Eclipse handler for @Family.
 * TODO: add support for generics.
 */
@ProviderFor(EclipseAnnotationHandler.class)
public class HandleFamily extends EclipseAnnotationHandler<Family> {

	/**
	 * For debugging: logging messages to file.
	 */
	Debug debug;
	
	/**
	 * Annotation itself.
	 */
	Annotation ast;
	
	/**
	 * Annotation as an EclipseNode in the AST.
	 */
	EclipseNode annotationNode;
	
	/**
	 * The annotated class/interface as an EclipseNode in the AST. 
	 */
	EclipseNode me;
	
	/**
	 * The annotated class/interface as a TypeDeclaration.
	 */
	TypeDeclaration meDecl;
	
	/**
	 * Some constants that are necessary when invoking some methods.
	 * Potentially useful for indicating the line number of showing errors/warnings.
	 */
	int pS, pE;
	long p;
	
	/**
	 * The inherited method that needs implementation as an EclipseHandler.
	 * Warning: please pass absolute path of file to debug.
	 */
	@Override public void handle(AnnotationValues<Family> annotation, Annotation ast, EclipseNode annotationNode) {
		debug = new Debug("C:\\Users\\v-haoyuz\\workspace_newFamily\\lombok\\log.txt");
		debug.clear();
		
		this.ast = ast;
		this.annotationNode = annotationNode;
		this.me = annotationNode.up();
		this.pS = ast.sourceStart;
		this.pE = ast.sourceEnd;
		this.p = (long) pS << 32 | pE;
		this.meDecl = (TypeDeclaration) me.get();
		
		if (!this.preCheck()) return;
		
//		debug.log("Processing " + String.valueOf(meDecl.name) + "...\n", true);
		
		new FamilyPolymorphism(this.me, this.annotationNode);
		
//		debug.log("Finished processing " + String.valueOf(meDecl.name) + ".\n", true);
	}
	
	/**
	 * A user may accidentally write code like:
	 * interface A { interface B { interface C extends A {}}}
	 * which compiles but is not recommended in our family polymorphism system,
	 * furthermore, Eclipse will crash due to infinite loop in the steps afterwards.
	 * This function avoids this situation by checking that case in advance.
	 * @param node: the EclipseNode for checking
	 * @param outs: intermediate variable during recursion
	 * @return: empty string if such a loop does not exist, or the involved type name.
	 */
	private String checkInfinite(EclipseNode node, ArrayList<String> outs) {
		if (!(node.get() instanceof TypeDeclaration)) return "";
		TypeDeclaration nDecl = (TypeDeclaration) node.get();
		if (nDecl.superInterfaces != null) {
			for (TypeReference t : nDecl.superInterfaces) {
				EclipseNode tDecl = Path.getTypeDecl(t.toString(), node);
				if (tDecl != null && outs.contains(Path.getAbsolutePathForType(tDecl))) {
					return String.valueOf(nDecl.name);
				}
			}
		}
		ArrayList<String> newOuts = new ArrayList<String>();
		newOuts.addAll(outs);
		if (outs.size() > 0) newOuts.add(outs.get(outs.size() - 1) + "." + String.valueOf(nDecl.name));
		else newOuts.add(String.valueOf(nDecl.name));
		if (node.down() != null) {
			for (EclipseNode child : node.down()) {
				String childIsInfinite = checkInfinite(child, newOuts);
				if (!childIsInfinite.isEmpty()) return childIsInfinite;
			}
		}
		return "";
	}
	
	
	/**
	 * A user may accidentally write code like:
	 * interface A extends A {...}
	 * which does not compile, yet Eclipse will crash due to infinite loop in the steps afterwards.
	 * This function avoids this situation by checking that case in advance.
	 * @param node: the EclipseNode for checking
	 * @return: empty string if such a loop does not exist, or the involved type name.
	 */
	private String checkExtendSelf(EclipseNode node) {
		if (!(node.get() instanceof TypeDeclaration)) return "";
		TypeDeclaration nDecl = (TypeDeclaration) node.get();
		if (nDecl.superInterfaces != null) {
			for (TypeReference t : nDecl.superInterfaces)
				if (General.removeTypeArgs(t.toString()).equals(String.valueOf(nDecl.name))) {
					return String.valueOf(nDecl.name);
				}
		}
		if (node.down() != null) {
			for (EclipseNode child : node.down()) {
				String childExtendSelf = checkExtendSelf(child);
				if (!childExtendSelf.isEmpty()) return childExtendSelf;
			}
		}
		return "";
	}
	
	/**
	 * Check the cyclic inheritance accidentally caused by user.
	 * This helps to avoid infinite loop in the steps afterwards.
	 * @return: true if the check passes, false otherwise.
	 */
	private boolean preCheck() {
		String extendSelf = checkExtendSelf(me);
		if (!extendSelf.isEmpty()) {
			println("Error: Type " + extendSelf + " extends itself.");
			return false;
		}
		
		String meAbsolutePath = Path.getAbsolutePathForType(me);
		int indexMePath = meAbsolutePath.lastIndexOf('.');
		ArrayList<String> tempList = new ArrayList<String>();
		if (indexMePath != -1) tempList.add(meAbsolutePath.substring(0, indexMePath));
		String isInfinite = checkInfinite(me, tempList);
		if (!isInfinite.isEmpty()) {
			println("Error: Member type " + isInfinite + " extends its enclosing type.");
			return false;
		}
		
		return true;
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
	
}
