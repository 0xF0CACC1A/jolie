/***************************************************************************
 *   Copyright (C) by Fabrizio Montesi                                     *
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


package jolie.runtime.expression;

import java.util.function.BiPredicate;

import jolie.process.TransformationReason;
import jolie.runtime.Value;


/**
 * @author Fabrizio Montesi TODO Clean up code.
 *
 */
public class CompareCondition implements Expression {
	private final Expression leftExpression, rightExpression;
	private final BiPredicate< Value, Value > compareOperator;

	public CompareCondition( Expression left, Expression right, BiPredicate< Value, Value > compareOperator ) {
		this.leftExpression = left;
		this.rightExpression = right;
		this.compareOperator = compareOperator;
	}

	@Override
	public Expression cloneExpression( TransformationReason reason ) {
		return new CompareCondition(
			leftExpression.cloneExpression( reason ),
			rightExpression.cloneExpression( reason ),
			compareOperator );
	}

	@Override
	public Value evaluate() {
		// Check if left side has array wildcards (e.g., $.tags[*] == "red")
		if( leftExpression instanceof CurrentValueExpression ) {
			CurrentValueExpression cvExpr = (CurrentValueExpression) leftExpression;
			if( cvExpr.hasArrayWildcards() ) {
				// Special handling: evaluate right side, then check array wildcard
				Value rightValue = rightExpression.evaluate();
				boolean matches = cvExpr.evaluateArrayWildcardComparison( rightValue, compareOperator );
				return Value.create( matches );
			}
		}

		// Check if right side has array wildcards (e.g., "red" == $.tags[*])
		if( rightExpression instanceof CurrentValueExpression ) {
			CurrentValueExpression cvExpr = (CurrentValueExpression) rightExpression;
			if( cvExpr.hasArrayWildcards() ) {
				// Special handling: evaluate left side, then check array wildcard
				// Flip the operator for right-side wildcards
				Value leftValue = leftExpression.evaluate();
				boolean matches =
					cvExpr.evaluateArrayWildcardComparison( leftValue, ( v1, v2 ) -> compareOperator.test( v2, v1 ) );
				return Value.create( matches );
			}
		}

		// Normal case: no array wildcards
		return Value.create( compareOperator.test( leftExpression.evaluate(), rightExpression.evaluate() ) );
	}

	public Expression leftExpression() {
		return leftExpression;
	}

	public Expression rightExpression() {
		return rightExpression;
	}
}
