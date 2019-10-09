package org.apache.flink.datalog.planner.delegation;

import org.apache.calcite.config.Lex;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.plan.Context;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.type.RelDataTypeSystem;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.util.ChainedSqlOperatorTable;
import org.apache.calcite.sql.validate.SqlConformance;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.flink.sql.parser.impl.FlinkSqlParserImpl;
import org.apache.flink.table.api.TableConfig;
import org.apache.flink.table.catalog.FunctionCatalog;
import org.apache.flink.table.planner.calcite.*;
import org.apache.flink.table.planner.catalog.FunctionCatalogOperatorTable;
import org.apache.flink.table.planner.codegen.ExpressionReducer;
import org.apache.flink.table.planner.functions.sql.FlinkSqlOperatorTable;
import org.apache.flink.table.planner.plan.FlinkCalciteCatalogReader;
import org.apache.flink.table.planner.utils.JavaScalaConversionUtil;
import org.apache.flink.table.planner.utils.TableConfigUtils;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/*
 * Utility class to create FrameworkConfig used to create a corresponding Planner. currently it is same as PlannerContext, later we need to add/remove/modify some methods from it....
 * */
public class DatalogPlannerContext {
	private final RelDataTypeSystem typeSystem = new FlinkTypeSystem();
	private final FlinkTypeFactory typeFactory = new FlinkTypeFactory(typeSystem);
	private final TableConfig tableConfig;
	private final FunctionCatalog functionCatalog;
	private final Context context;
	private final CalciteSchema rootSchema;
	private final FrameworkConfig frameworkConfig;

	public DatalogPlannerContext(
		TableConfig tableConfig,
		FunctionCatalog functionCatalog,
		CalciteSchema rootSchema) {
		this.tableConfig = tableConfig;
		this.functionCatalog = functionCatalog;
		this.context = new FlinkContextImpl(tableConfig, functionCatalog);
		this.rootSchema = rootSchema;

		frameworkConfig = createFrameworkConfig();
		RelOptPlanner planner = new VolcanoPlanner(frameworkConfig.getCostFactory(), frameworkConfig.getContext());
		planner.setExecutor(frameworkConfig.getExecutor());
	}

	public FrameworkConfig createFrameworkConfig() {
		return Frameworks.newConfigBuilder()
			.defaultSchema(rootSchema.plus())
			.typeSystem(typeSystem)
			.operatorTable(getSqlOperatorTable(getCalciteConfig(tableConfig), functionCatalog))
			.executor(new ExpressionReducer(tableConfig, false))
			.context(context)
			.build();
	}

	public FlinkTypeFactory getTypeFactory() {
		return typeFactory;
	}

	public FlinkPlannerImpl createFlinkPlanner(String currentCatalog, String currentDatabase) {
//		RelOptCluster cluster;
//		return new FlinkPlannerImpl(
//			createFrameworkConfig(),
//			isLenient -> createCatalogReader(isLenient, currentCatalog, currentDatabase),
//			typeFactory,
//			cluster);

		return null;
	}

	private FlinkCalciteCatalogReader createCatalogReader(
		boolean lenientCaseSensitivity,
		String currentCatalog,
		String currentDatabase) {
		SqlParser.Config sqlParserConfig = getSqlParserConfig();
		final boolean caseSensitive;
		if (lenientCaseSensitivity) {
			caseSensitive = false;
		} else {
			caseSensitive = sqlParserConfig.caseSensitive();
		}

		SqlParser.Config newSqlParserConfig = SqlParser.configBuilder(sqlParserConfig)
			.setCaseSensitive(caseSensitive)
			.build();

		SchemaPlus rootSchema = getRootSchema(this.rootSchema.plus());
		return new FlinkCalciteCatalogReader(
			CalciteSchema.from(rootSchema),
			asList(
				asList(currentCatalog, currentDatabase),
				singletonList(currentCatalog)
			),
			typeFactory,
			CalciteConfig$.MODULE$.connectionConfig(newSqlParserConfig));
	}

	private SchemaPlus getRootSchema(SchemaPlus schema) {
		if (schema.getParentSchema() == null) {
			return schema;
		} else {
			return getRootSchema(schema.getParentSchema());
		}
	}

	private CalciteConfig getCalciteConfig(TableConfig tableConfig) {
		return TableConfigUtils.getCalciteConfig(tableConfig);
	}

	private SqlOperatorTable getSqlOperatorTable(CalciteConfig calciteConfig, FunctionCatalog functionCatalog) {
		return JavaScalaConversionUtil.toJava(calciteConfig.getSqlOperatorTable()).map(operatorTable -> {
				if (calciteConfig.replacesSqlOperatorTable()) {
					return operatorTable;
				} else {
					return ChainedSqlOperatorTable.of(getBuiltinSqlOperatorTable(functionCatalog), operatorTable);
				}
			}
		).orElseGet(() -> getBuiltinSqlOperatorTable(functionCatalog));
	}

	private SqlOperatorTable getBuiltinSqlOperatorTable(FunctionCatalog functionCatalog) {
		return ChainedSqlOperatorTable.of(
			new FunctionCatalogOperatorTable(functionCatalog, typeFactory),
			FlinkSqlOperatorTable.instance());
	}

	private SqlParser.Config getSqlParserConfig() {
		return JavaScalaConversionUtil.toJava(getCalciteConfig(tableConfig).getSqlParserConfig()).orElseGet(
			// we use Java lex because back ticks are easier than double quotes in programming
			// and cases are preserved
			() -> SqlParser
				.configBuilder()
				.setParserFactory(FlinkSqlParserImpl.FACTORY)
				.setConformance(getSqlConformance())
				.setLex(Lex.JAVA)
				.setIdentifierMaxLength(256)
				.build());
	}

	private SqlConformance getSqlConformance() {
		return null;
	}
}
