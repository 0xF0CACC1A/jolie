/***************************************************************************
 *   Copyright (C) 2010 by Fabrizio Montesi <famontesi@gmail.com>          *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU Library General Public License as       *
 *   published by the Free Software Foundation; either version 2 of the    *
 *   License, or (at your option) any later version.                       *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU Library General Public     *
 *   License along with this program; if not, write to the                 *
 *   Free Software Foundation, Inc.,                                       *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
 *                                                                         *
 *   For details about the authors of this software, see the AUTHORS file. *
 ***************************************************************************/

package jolie.lang.parse.util.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jolie.lang.parse.UnitOLVisitor;
import jolie.lang.parse.ast.AddAssignStatement;
import jolie.lang.parse.ast.AssignStatement;
import jolie.lang.parse.ast.CompareConditionNode;
import jolie.lang.parse.ast.CompensateStatement;
import jolie.lang.parse.ast.CorrelationSetInfo;
import jolie.lang.parse.ast.CurrentHandlerStatement;
import jolie.lang.parse.ast.DeepCopyStatement;
import jolie.lang.parse.ast.DefinitionCallStatement;
import jolie.lang.parse.ast.DefinitionNode;
import jolie.lang.parse.ast.DivideAssignStatement;
import jolie.lang.parse.ast.DocumentationComment;
import jolie.lang.parse.ast.EmbedServiceNode;
import jolie.lang.parse.ast.EmbeddedServiceNode;
import jolie.lang.parse.ast.ExecutionInfo;
import jolie.lang.parse.ast.ExitStatement;
import jolie.lang.parse.ast.ForEachArrayItemStatement;
import jolie.lang.parse.ast.ForEachSubNodeStatement;
import jolie.lang.parse.ast.ForStatement;
import jolie.lang.parse.ast.IfStatement;
import jolie.lang.parse.ast.ImportStatement;
import jolie.lang.parse.ast.InputPortInfo;
import jolie.lang.parse.ast.InstallFixedVariableExpressionNode;
import jolie.lang.parse.ast.InstallStatement;
import jolie.lang.parse.ast.InterfaceDefinition;
import jolie.lang.parse.ast.InterfaceExtenderDefinition;
import jolie.lang.parse.ast.LinkInStatement;
import jolie.lang.parse.ast.LinkOutStatement;
import jolie.lang.parse.ast.MultiplyAssignStatement;
import jolie.lang.parse.ast.NDChoiceStatement;
import jolie.lang.parse.ast.NotificationOperationStatement;
import jolie.lang.parse.ast.NullProcessStatement;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.ast.OneWayOperationDeclaration;
import jolie.lang.parse.ast.OneWayOperationStatement;
import jolie.lang.parse.ast.OutputPortInfo;
import jolie.lang.parse.ast.ParallelStatement;
import jolie.lang.parse.ast.PointerStatement;
import jolie.lang.parse.ast.PostDecrementStatement;
import jolie.lang.parse.ast.PostIncrementStatement;
import jolie.lang.parse.ast.PreDecrementStatement;
import jolie.lang.parse.ast.PreIncrementStatement;
import jolie.lang.parse.ast.Program;
import jolie.lang.parse.ast.ProvideUntilStatement;
import jolie.lang.parse.ast.RequestResponseOperationDeclaration;
import jolie.lang.parse.ast.RequestResponseOperationStatement;
import jolie.lang.parse.ast.RunStatement;
import jolie.lang.parse.ast.Scope;
import jolie.lang.parse.ast.SequenceStatement;
import jolie.lang.parse.ast.ServiceNode;
import jolie.lang.parse.ast.SolicitResponseOperationStatement;
import jolie.lang.parse.ast.SpawnStatement;
import jolie.lang.parse.ast.SubtractAssignStatement;
import jolie.lang.parse.ast.SynchronizedStatement;
import jolie.lang.parse.ast.ThrowStatement;
import jolie.lang.parse.ast.TypeCastExpressionNode;
import jolie.lang.parse.ast.UndefStatement;
import jolie.lang.parse.ast.SelectStatement;
import jolie.lang.parse.ast.ValueVectorSizeExpressionNode;
import jolie.lang.parse.ast.VariablePathNode;
import jolie.lang.parse.ast.WhileStatement;
import jolie.lang.parse.ast.courier.CourierChoiceStatement;
import jolie.lang.parse.ast.courier.CourierDefinitionNode;
import jolie.lang.parse.ast.courier.NotificationForwardStatement;
import jolie.lang.parse.ast.courier.SolicitResponseForwardStatement;
import jolie.lang.parse.ast.expression.*;
import jolie.lang.parse.ast.expression.CurrentValueNode;
import jolie.lang.parse.ast.types.TypeChoiceDefinition;
import jolie.lang.parse.ast.types.TypeDefinition;
import jolie.lang.parse.ast.types.TypeDefinitionLink;
import jolie.lang.parse.ast.types.TypeInlineDefinition;
import jolie.lang.parse.util.ProgramInspector;
import jolie.util.Pair;

/**
 * Visitor for creating a {@link ProgramInspectorImpl} object.
 *
 * @author Fabrizio Montesi
 */
public class ProgramInspectorCreatorVisitor implements UnitOLVisitor {
	private final Map< URI, List< InterfaceDefinition > > interfaces = new HashMap<>();
	private final Map< URI, List< InputPortInfo > > inputPorts = new HashMap<>();
	private final Map< URI, List< OutputPortInfo > > outputPorts = new HashMap<>();
	private final Map< URI, List< TypeDefinition > > types = new HashMap<>();
	private final Map< URI, List< EmbeddedServiceNode > > embeddedServices = new HashMap<>();
	private final Map< URI, Map< OLSyntaxNode, List< OLSyntaxNode > > > behaviouralDependencies = new HashMap<>();
	private final Map< URI, List< ServiceNode > > serviceNodes = new HashMap<>();
	private final Set< URI > sources = new HashSet<>();

	private OLSyntaxNode currentFirstInput = null;

	public ProgramInspectorCreatorVisitor( Program program ) {
		program.accept( this );
	}

	public ProgramInspector createInspector() {
		return new ProgramInspectorImpl(
			sources.toArray( new URI[ 0 ] ),
			types,
			interfaces,
			inputPorts,
			outputPorts,
			embeddedServices,
			behaviouralDependencies,
			serviceNodes );
	}

	private void encounteredNode( OLSyntaxNode n ) {
		sources.add( n.context().source() );
	}

	public void visit( Program n ) {
		for( OLSyntaxNode node : n.children() ) {
			node.accept( this );
		}
	}

	public void visit( InterfaceDefinition n ) {
		List< InterfaceDefinition > list = interfaces.computeIfAbsent( n.context().source(), k -> new LinkedList<>() );
		list.add( n );

		encounteredNode( n );
	}

	public void visit( TypeInlineDefinition n ) {
		List< TypeDefinition > list = types.computeIfAbsent( n.context().source(), k -> new LinkedList<>() );
		list.add( n );

		encounteredNode( n );
	}

	public void visit( TypeDefinitionLink n ) {
		List< TypeDefinition > list = types.computeIfAbsent( n.context().source(), k -> new LinkedList<>() );
		list.add( n );

		encounteredNode( n );
	}

	public void visit( InputPortInfo n ) {
		List< InputPortInfo > list = inputPorts.computeIfAbsent( n.context().source(), k -> new LinkedList<>() );
		list.add( n );
		encounteredNode( n );
	}

	public void visit( OutputPortInfo n ) {
		List< OutputPortInfo > list = outputPorts.computeIfAbsent( n.context().source(), k -> new LinkedList<>() );
		list.add( n );

		encounteredNode( n );
	}

	public void visit( EmbeddedServiceNode n ) {
		List< EmbeddedServiceNode > list =
			embeddedServices.computeIfAbsent( n.context().source(), k -> new LinkedList<>() );
		list.add( n );

		encounteredNode( n );
	}

	public void visit( OneWayOperationDeclaration decl ) {}

	public void visit( RequestResponseOperationDeclaration decl ) {}

	public void visit( DefinitionNode n ) {
		n.body().accept( this );
	}

	public void visit( ParallelStatement n ) {
		for( OLSyntaxNode node : n.children() ) {
			node.accept( this );
		}
	}

	public void visit( SequenceStatement n ) {
		for( OLSyntaxNode node : n.children() ) {
			node.accept( this );
		}
	}

	public void visit( NDChoiceStatement n ) {
		if( currentFirstInput != null ) {
			for( Pair< OLSyntaxNode, OLSyntaxNode > pair : n.children() ) {
				addOlSyntaxNodeToBehaviouralDependencies( pair.key() );
				pair.value().accept( this );
			}
		} else {
			for( Pair< OLSyntaxNode, OLSyntaxNode > pair : n.children() ) {
				if( pair.key() instanceof OneWayOperationStatement ) {
					currentFirstInput = pair.key();
				} else if( pair.key() instanceof RequestResponseOperationStatement ) {
					currentFirstInput = pair.key();
					((RequestResponseOperationStatement) pair.key()).process().accept( this );
				}
				pair.value().accept( this );
				currentFirstInput = null;
			}

		}
	}

	public void visit( OneWayOperationStatement n ) {
		if( currentFirstInput == null ) {
			currentFirstInput = n;
		} else {
			addOlSyntaxNodeToBehaviouralDependencies( n );
		}
	}

	public void visit( RequestResponseOperationStatement n ) {
		if( currentFirstInput == null ) {
			currentFirstInput = n;
		} else {
			addOlSyntaxNodeToBehaviouralDependencies( n );
		}
		n.process().accept( this );
	}

	public void visit( NotificationOperationStatement n ) {
		addOlSyntaxNodeToBehaviouralDependencies( n );
	}

	public void visit( SolicitResponseOperationStatement n ) {
		addOlSyntaxNodeToBehaviouralDependencies( n );
	}

	public void visit( LinkInStatement n ) {}

	public void visit( LinkOutStatement n ) {}

	public void visit( AssignStatement n ) {}

	public void visit( IfStatement n ) {
		for( Pair< OLSyntaxNode, OLSyntaxNode > pair : n.children() ) {
			pair.key().accept( this );
			pair.value().accept( this );
		}
		if( n.elseProcess() != null ) {
			n.elseProcess().accept( this );
		}
	}

	public void visit( DefinitionCallStatement n ) {}

	public void visit( WhileStatement n ) {
		n.body().accept( this );
	}

	public void visit( OrConditionNode n ) {}

	public void visit( AndConditionNode n ) {}

	public void visit( NotExpressionNode n ) {}

	public void visit( CompareConditionNode n ) {}

	public void visit( ConstantIntegerExpression n ) {}

	public void visit( ConstantLongExpression n ) {}

	public void visit( ConstantBoolExpression n ) {}

	public void visit( ConstantDoubleExpression n ) {}

	public void visit( ConstantStringExpression n ) {}

	public void visit( CurrentValueNode n ) {}


	public void visit( ProductExpressionNode n ) {}

	public void visit( SumExpressionNode n ) {}

	public void visit( VariableExpressionNode n ) {}

	public void visit( NullProcessStatement n ) {}

	public void visit( Scope n ) {
		n.body().accept( this );
	}

	public void visit( InstallStatement n ) {
		for( int i = 0; i < n.handlersFunction().pairs().length; i++ ) {
			n.handlersFunction().pairs()[ i ].value().accept( this );
		}
	}

	public void visit( CompensateStatement n ) {}

	public void visit( ThrowStatement n ) {}

	public void visit( ExitStatement n ) {}

	public void visit( ExecutionInfo n ) {}

	public void visit( CorrelationSetInfo n ) {}

	public void visit( PointerStatement n ) {}

	public void visit( DeepCopyStatement n ) {}

	public void visit( RunStatement n ) {}

	public void visit( UndefStatement n ) {}

	public void visit( SelectStatement n ) {}

	public void visit( ValueVectorSizeExpressionNode n ) {}

	public void visit( PreIncrementStatement n ) {}

	public void visit( PostIncrementStatement n ) {}

	public void visit( PreDecrementStatement n ) {}

	public void visit( PostDecrementStatement n ) {}

	public void visit( ForStatement n ) {
		n.body().accept( this );
	}

	public void visit( ForEachSubNodeStatement n ) {
		n.body().accept( this );
	}

	public void visit( ForEachArrayItemStatement n ) {
		n.body().accept( this );
	}

	public void visit( SpawnStatement n ) {
		n.body().accept( this );
	}

	public void visit( IsTypeExpressionNode n ) {}

	public void visit( TypeCastExpressionNode n ) {}

	public void visit( SynchronizedStatement n ) {
		n.body().accept( this );
	}

	public void visit( CurrentHandlerStatement n ) {}

	public void visit( InstallFixedVariableExpressionNode n ) {}

	public void visit( VariablePathNode n ) {}

	public void visit( DocumentationComment n ) {}

	public void visit( AddAssignStatement n ) {}

	public void visit( SubtractAssignStatement n ) {}

	public void visit( MultiplyAssignStatement n ) {}

	public void visit( DivideAssignStatement n ) {}

	public void visit( FreshValueExpressionNode n ) {}

	public void visit( InterfaceExtenderDefinition n ) {}

	public void visit( CourierDefinitionNode n ) {}

	public void visit( CourierChoiceStatement n ) {}

	public void visit( NotificationForwardStatement n ) {}

	public void visit( InstanceOfExpressionNode n ) {}

	public void visit( SolicitResponseForwardStatement n ) {
		addOlSyntaxNodeToBehaviouralDependencies( n );
	}

	public void visit( InlineTreeExpressionNode n ) {}

	public void visit( VoidExpressionNode n ) {}

	public void visit( ProvideUntilStatement n ) {
		n.provide().accept( this );
		n.until().accept( this );
	}

	public void visit( TypeChoiceDefinition n ) {
		List< TypeDefinition > list = types.computeIfAbsent( n.context().source(), k -> new LinkedList<>() );
		list.add( n );

		encounteredNode( n );
	}

	private void addOlSyntaxNodeToBehaviouralDependencies( OLSyntaxNode n ) {
		if( currentFirstInput != null ) {
			behaviouralDependencies.computeIfAbsent( n.context().source(), k -> new HashMap<>() );
			Map< OLSyntaxNode, List< OLSyntaxNode > > sourceBehaviouralDependencies =
				behaviouralDependencies.get( n.context().source() );
			sourceBehaviouralDependencies.computeIfAbsent( currentFirstInput, k -> new ArrayList<>() );
			sourceBehaviouralDependencies.get( currentFirstInput ).add( n );
		}
	}

	public void visit( ImportStatement n ) {}

	public void visit( ServiceNode n ) {
		List< ServiceNode > list = serviceNodes.get( n.context().source() );
		if( list == null ) {
			list = new LinkedList<>();
			serviceNodes.put( n.context().source(), list );
		}
		list.add( n );
		encounteredNode( n );
		n.program().accept( this );
	}

	public void visit( EmbedServiceNode n ) {}

	public void visit( SolicitResponseExpressionNode n ) {}

	public void visit( IfExpressionNode n ) {
		n.guard().accept( this );
		n.thenExpression().accept( this );
		n.elseExpression().accept( this );
	}

	public void visit( SelectExpressionNode n ) {
		n.fromVariable().accept( this );
	}
}
