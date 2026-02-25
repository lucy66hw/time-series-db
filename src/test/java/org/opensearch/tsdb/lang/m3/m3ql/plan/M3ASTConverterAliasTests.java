/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.tsdb.lang.m3.m3ql.plan;

import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.tsdb.lang.m3.M3TestUtils;
import org.opensearch.tsdb.lang.m3.m3ql.parser.generated.M3QLParser;
import org.opensearch.tsdb.lang.m3.m3ql.plan.nodes.M3PlanNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Unit tests for M3QL function aliases in M3ASTConverter.
 * These tests run once (not parameterized) since they test static parser functionality.
 */
public class M3ASTConverterAliasTests extends OpenSearchTestCase {

    /**
     * Test that ratio alias produces AS_PERCENT node.
     */
    public void testRatioAliasProducesAsPercentNode() {
        assertBinaryAliasProducesNode("ratio", "AS_PERCENT");
    }

    /**
     * Test that divide alias produces DIVIDE_SERIES node.
     */
    public void testDivideAliasProducesDivideSeriesNode() {
        assertBinaryAliasProducesNode("divide", "DIVIDE_SERIES");
    }

    /**
     * Test that subtract alias produces DIFF node.
     */
    public void testSubtractAliasProducesDiffNode() {
        assertBinaryAliasProducesNode("subtract", "DIFF");
    }

    /**
     * Test that sortSeries alias produces SORT node.
     */
    public void testSortSeriesAliasProducesSortNode() {
        assertUnaryAliasProducesNode("sortSeries", "SORT");
    }

    // -----------------------------------------------------------------------
    // burnRate / asBurnRate
    // -----------------------------------------------------------------------

    /**
     * Test that burnRate produces BURN_RATE node.
     */
    public void testBurnRateDesugarsToTransformNull() {
        assertBinaryAliasProducesNode("burnRate", "1d 99.9", "TRANSFORM_NULL");
    }

    /**
     * Test that asBurnRate alias also desugars to TRANSFORM_NULL node (same as burnRate).
     */
    public void testAsBurnRateAliasDesugarsToTransformNull() {
        assertBinaryAliasProducesNode("asBurnRate", "1h 99.9", "TRANSFORM_NULL");
    }

    /**
     * Test that asBurnRate rejects SLO = 0.
     */
    public void testAsBurnRateRejectsSloZero() {
        assertBurnRateThrows("asBurnRate", "1h", "0");
    }

    /**
     * Test that asBurnRate rejects SLO = 100.
     */
    public void testAsBurnRateRejectsSlo100() {
        assertBurnRateThrows("asBurnRate", "1h", "100");
    }

    /**
     * Test that asBurnRate rejects SLO > 100.
     */
    public void testAsBurnRateRejectsSloAbove100() {
        assertBurnRateThrows("asBurnRate", "1h", "150");
    }

    // -----------------------------------------------------------------------
    // multiBurnRate / asMultiBurnRate
    // -----------------------------------------------------------------------

    /**
     * Test that multiBurnRate desugars to AGG(MIN) node.
     */
    public void testMultiBurnRateDesugarsToAggMin() {
        assertBinaryAliasProducesNode("multiBurnRate", "1d 2h 99.9", "AGG(MIN");
    }

    /**
     * Test that asMultiBurnRate alias also desugars to AGG(MIN) node (same as multiBurnRate).
     */
    public void testAsMultiBurnRateAliasDesugarsToAggMin() {
        assertBinaryAliasProducesNode("asMultiBurnRate", "1d 2h 99.9", "AGG(MIN");
    }

    /**
     * Test that multiBurnRate desugars to two TRANSFORM_NULL chains.
     */
    public void testMultiBurnRateDesugarsToTwoTransformNullChains() {
        String query = "fetch name:a | multiBurnRate(fetch name:b) 1d 2h 99.9";
        try (M3PlannerContext context = M3PlannerContext.create()) {
            M3ASTConverter converter = new M3ASTConverter(context);
            String plan = getPlanString(converter.buildPlan(M3QLParser.parse(query, true)));
            assertEquals("multiBurnRate should contain exactly 2 TRANSFORM_NULL nodes", 2, plan.split("TRANSFORM_NULL", -1).length - 1);
        } catch (Exception e) {
            fail("Failed to test multiBurnRate: " + e.getMessage());
        }
    }

    /**
     * Test that multiBurnRate rejects SLO = 0.
     */
    public void testMultiBurnRateRejectsSloZero() {
        assertMultiBurnRateThrows("multiBurnRate", "1d", "2h", "0");
    }

    /**
     * Test that multiBurnRate rejects SLO = 100.
     */
    public void testMultiBurnRateRejectsSlo100() {
        assertMultiBurnRateThrows("multiBurnRate", "1d", "2h", "100");
    }

    /**
     * Test that multiBurnRate rejects SLO > 100.
     */
    public void testMultiBurnRateRejectsSloAbove100() {
        assertMultiBurnRateThrows("multiBurnRate", "1d", "2h", "150");
    }

    // -----------------------------------------------------------------------
    // burnRateMultiplier
    // -----------------------------------------------------------------------

    /**
     * Test that burnRateMultiplier desugars to SCALE node.
     */
    public void testBurnRateMultiplierDesugarsToScale() {
        assertUnaryDesugarProducesNode("burnRateMultiplier", "99.9", "SCALE");
    }

    /**
     * Test that burnRateMultiplier rejects SLO = 0.
     */
    public void testBurnRateMultiplierRejectsSloZero() {
        assertBurnRateMultiplierThrows("0");
    }

    /**
     * Test that burnRateMultiplier rejects SLO = 100.
     */
    public void testBurnRateMultiplierRejectsSlo100() {
        assertBurnRateMultiplierThrows("100");
    }

    /**
     * Test that burnRateMultiplier rejects SLO > 100.
     */
    public void testBurnRateMultiplierRejectsSloAbove100() {
        assertBurnRateMultiplierThrows("150");
    }

    /**
     * Helper method to test that a binary function alias produces the expected node type.
     *
     * @param aliasName the binary alias function name (e.g., "ratio", "divide", "subtract")
     * @param expectedNodeType the expected node type in the plan (e.g., "AS_PERCENT", "DIVIDE_SERIES", "DIFF")
     */
    private void assertBinaryAliasProducesNode(String aliasName, String expectedNodeType) {
        assertBinaryAliasProducesNode(aliasName, "", expectedNodeType);
    }

    /**
     * Helper method to test that a binary function alias with extra parameters produces the expected node type.
     *
     * @param aliasName the binary alias function name (e.g., "burnRate", "asBurnRate")
     * @param extraParams additional parameters appended after the pipeline argument (e.g., "1h 99.9")
     * @param expectedNodeType the expected node type in the plan (e.g., "AS_BURN_RATE")
     */
    private void assertBinaryAliasProducesNode(String aliasName, String extraParams, String expectedNodeType) {
        String suffix = extraParams.isEmpty() ? "" : " " + extraParams;
        String query = "fetch name:a | " + aliasName + "(fetch name:b)" + suffix;
        try (M3PlannerContext context = M3PlannerContext.create()) {
            M3ASTConverter converter = new M3ASTConverter(context);
            String plan = getPlanString(converter.buildPlan(M3QLParser.parse(query, true)));
            assertTrue(aliasName + " alias should produce " + expectedNodeType + " node", plan.contains(expectedNodeType));
        } catch (Exception e) {
            fail("Failed to test " + aliasName + " alias: " + e.getMessage());
        }
    }

    /**
     * Helper method to test that a unary function alias produces the expected node type.
     *
     * @param aliasName the unary alias function name (e.g., "sortSeries")
     * @param expectedNodeType the expected node type in the plan (e.g., "SORT")
     */
    private void assertUnaryAliasProducesNode(String aliasName, String expectedNodeType) {
        String query = "fetch name:a | " + aliasName + " avg";
        try (M3PlannerContext context = M3PlannerContext.create()) {
            M3ASTConverter converter = new M3ASTConverter(context);
            String plan = getPlanString(converter.buildPlan(M3QLParser.parse(query, true)));
            assertTrue(aliasName + " alias should produce " + expectedNodeType + " node", plan.contains(expectedNodeType));
        } catch (Exception e) {
            fail("Failed to test " + aliasName + " alias: " + e.getMessage());
        }
    }

    /**
     * Helper method to test that a unary desugared function produces the expected node type.
     *
     * @param functionName the unary function name (e.g., "burnRateMultiplier")
     * @param param the parameter to pass (e.g., "99.9")
     * @param expectedNodeType the expected node type in the plan (e.g., "SCALE")
     */
    private void assertUnaryDesugarProducesNode(String functionName, String param, String expectedNodeType) {
        String query = "fetch name:a | " + functionName + " " + param;
        try (M3PlannerContext context = M3PlannerContext.create()) {
            M3ASTConverter converter = new M3ASTConverter(context);
            String plan = getPlanString(converter.buildPlan(M3QLParser.parse(query, true)));
            assertTrue(functionName + " should produce " + expectedNodeType + " node", plan.contains(expectedNodeType));
        } catch (Exception e) {
            fail("Failed to test " + functionName + ": " + e.getMessage());
        }
    }

    private void assertBurnRateMultiplierThrows(String slo) {
        String query = "fetch name:a | burnRateMultiplier " + slo;
        try (M3PlannerContext context = M3PlannerContext.create()) {
            expectThrows(Exception.class, () -> new M3ASTConverter(context).buildPlan(M3QLParser.parse(query, true)));
        } catch (Exception e) {
            if (!(e instanceof IllegalArgumentException)) {
                fail("Unexpected exception from context.close(): " + e.getMessage());
            }
        }
    }

    private void assertMultiBurnRateThrows(String fn, String interval1, String interval2, String slo) {
        String query = "fetch name:a | " + fn + "(fetch name:b) " + interval1 + " " + interval2 + " " + slo;
        try (M3PlannerContext context = M3PlannerContext.create()) {
            expectThrows(Exception.class, () -> new M3ASTConverter(context).buildPlan(M3QLParser.parse(query, true)));
        } catch (Exception e) {
            if (!(e instanceof IllegalArgumentException)) {
                fail("Unexpected exception from context.close(): " + e.getMessage());
            }
        }
    }

    /**
     * Helper method to convert M3PlanNode to string for assertions.
     */
    private void assertBurnRateThrows(String fn, String interval, String slo) {
        String query = "fetch name:a | " + fn + "(fetch name:b) " + interval + " " + slo;
        try (M3PlannerContext context = M3PlannerContext.create()) {
            expectThrows(Exception.class, () -> new M3ASTConverter(context).buildPlan(M3QLParser.parse(query, true)));
        } catch (Exception e) {
            if (!(e instanceof IllegalArgumentException)) {
                fail("Unexpected exception from context.close(): " + e.getMessage());
            }
        }
    }

    private String getPlanString(M3PlanNode planNode) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
            M3TestUtils.printPlan(planNode, 0, ps);
            return baos.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
