package lama.mysql.ast;

import lama.IgnoreMeException;
import lama.Randomly;
import lama.mysql.MySQLSchema.MySQLDataType;
import lama.mysql.ast.MySQLCastOperation.CastType;

public class MySQLComputableFunction extends MySQLExpression {

	private final MySQLFunction func;
	private final MySQLExpression[] args;

	public MySQLComputableFunction(MySQLFunction func, MySQLExpression... args) {
		assert args.length == func.getNrArgs();
		this.func = func;
		this.args = args;
	}

	public MySQLFunction getFunction() {
		return func;
	}

	public MySQLExpression[] getArguments() {
		return args;
	}

	public enum MySQLFunction {
		ABS(1, "ABS") {
			@Override
			public MySQLConstant apply(MySQLConstant[] args, MySQLExpression[] origArgs) {
				if (args[0].isNull()) {
					return MySQLConstant.createNullConstant();
				}
				MySQLConstant intVal = args[0].castAs(CastType.SIGNED);
				return MySQLConstant.createIntConstant(Math.abs(intVal.getInt()));
			}
		},
		/**
		 * @see https://dev.mysql.com/doc/refman/8.0/en/bit-functions.html#function_bit-count
		 */
		BIT_COUNT(1, "BIT_COUNT") {

			@Override
			public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression[] args) {
				MySQLConstant arg = evaluatedArgs[0];
				if (arg.isNull()) {
					return MySQLConstant.createNullConstant();
				} else {
					long val = arg.castAs(CastType.SIGNED).getInt();
					return MySQLConstant.createIntConstant(Long.bitCount(val));
				}
			}

		},
		BENCHMARK(2, "BENCHMARK") {

			@Override
			public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression[] args) {
				if (evaluatedArgs[0].isNull()) {
					return MySQLConstant.createNullConstant();
				}
				if (evaluatedArgs[0].castAs(CastType.SIGNED).getInt() < 0) {
					return MySQLConstant.createNullConstant();
				}
				return MySQLConstant.createIntConstant(0);
			}

		},
		COALESCE(2, "COALESCE") {

			@Override
			public MySQLConstant apply(MySQLConstant[] args, MySQLExpression[] origArgs) {
				MySQLConstant result = MySQLConstant.createNullConstant();
				for (MySQLConstant arg : args) {
					if (!arg.isNull()) {
						result = MySQLConstant.createStringConstant(arg.castAsString());
						break;
					}
				}
				return castToMostGeneralType(result, origArgs);
			}

			@Override
			public boolean isVariadic() {
				return true;
			}

		},
		/**
		 * @see https://dev.mysql.com/doc/refman/8.0/en/control-flow-functions.html#function_if
		 */
		IF(3, "IF") {

			@Override
			public MySQLConstant apply(MySQLConstant[] args, MySQLExpression[] origArgs) {
				MySQLConstant cond = args[0];
				MySQLConstant left = args[1];
				MySQLConstant right = args[2];
				MySQLConstant result;
				if (cond.isNull() || !cond.asBooleanNotNull()) {
					result = right;
				} else {
					result = left;
				}
				return castToMostGeneralType(result, new MySQLExpression[] { origArgs[1], origArgs[2] });

			}

		},
		/**
		 * @see https://dev.mysql.com/doc/refman/8.0/en/control-flow-functions.html#function_ifnull
		 */
		IFNULL(2, "IFNULL") {

			@Override
			public MySQLConstant apply(MySQLConstant[] args, MySQLExpression[] origArgs) {
				MySQLConstant result;
				if (args[0].isNull()) {
					result = args[1];
				} else {
					result = args[0];
				}
				return castToMostGeneralType(result, origArgs);
			}

		};

		private String functionName;
		final int nrArgs;

		private MySQLFunction(int nrArgs, String functionName) {
			this.nrArgs = nrArgs;
			this.functionName = functionName;
		}

		/**
		 * Gets the number of arguments if the function is non-variadic. If the function
		 * is variadic, the minimum number of arguments is returned.
		 */
		public int getNrArgs() {
			return nrArgs;
		}

		public abstract MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression[] args);

		public static MySQLFunction getRandomFunction() {
			return Randomly.fromOptions(values());
		}

		@Override
		public String toString() {
			return functionName;
		}

		public boolean isVariadic() {
			return false;
		}

		public String getName() {
			return functionName;
		}
	}

	@Override
	public MySQLConstant getExpectedValue() {
		MySQLConstant[] constants = new MySQLConstant[args.length];
		for (int i = 0; i < constants.length; i++) {
			constants[i] = args[i].getExpectedValue();
			/* workaround for https://bugs.mysql.com/bug.php?id=95938 */
			if (constants[i].isString()) {
				String text = constants[i].castAsString();
				while ((text.startsWith(" ") || text.startsWith("\t")) && text.length() > 0) {
					text = text.substring(1);
				}
				if (text.length() > 0 && (text.startsWith("\n") || text.startsWith("."))) {
					throw new IgnoreMeException();
				}
			}
		}
		return func.apply(constants, args);
	}

	public static MySQLConstant castToMostGeneralType(MySQLConstant cons, MySQLExpression... typeExpressions) {
		if (cons.isNull()) {
			return cons;
		}
		MySQLDataType type = getMostGeneralType(typeExpressions);
		switch (type) {
		case INT:
			return MySQLConstant.createIntConstant(cons.castAs(CastType.SIGNED).getInt());
		case VARCHAR:
			return MySQLConstant.createStringConstant(cons.castAsString());
		default:
			throw new AssertionError(type);
		}
	}

	public static MySQLDataType getMostGeneralType(MySQLExpression... expressions) {
		MySQLDataType type = null;
		for (MySQLExpression expr : expressions) {
			MySQLDataType exprType;
			if (expr instanceof MySQLColumnValue) {
				exprType = ((MySQLColumnValue) expr).getColumn().getColumnType();
			} else {
				exprType = expr.getExpectedValue().getType();
			}
			if (type == null) {
				type = exprType;
			} else if (exprType == MySQLDataType.VARCHAR) {
				type = MySQLDataType.VARCHAR;
			}

		}
		return type;
	}

}