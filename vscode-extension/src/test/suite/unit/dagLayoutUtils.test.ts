import * as assert from 'assert';
import {
  simplifyLabel,
  buildTooltip,
  getTypeColor,
  getTypeIndicator,
  formatLargeNumber,
  truncateValue,
  formatJsonPreview,
  formatValuePreview,
  createHexagonPoints,
  computeLayout,
  calculateEdgePath,
  DagStructure,
  DagNode
} from '../../../utils/dagLayoutUtils';

/**
 * Unit tests for DAG layout utility functions.
 *
 * These tests verify the pure functions used for DAG layout computation,
 * type visualization, value formatting, and SVG rendering helpers.
 */
suite('DAG Layout Utility Functions', function() {
  suite('simplifyLabel', () => {
    test('should return empty string for undefined', () => {
      assert.strictEqual(simplifyLabel(undefined, 10), '');
    });

    test('should return empty string for empty string', () => {
      assert.strictEqual(simplifyLabel('', 10), '');
    });

    test('should return short labels unchanged', () => {
      assert.strictEqual(simplifyLabel('test', 10), 'test');
    });

    test('should truncate long labels with ellipsis', () => {
      const result = simplifyLabel('verylonglabelname', 10);
      assert.strictEqual(result.length, 10);
      assert.ok(result.endsWith('…'));
    });

    test('should strip UUID-like prefix', () => {
      assert.strictEqual(simplifyLabel('abc12345_varName', 20), 'varName');
    });

    test('should take last part of dot-separated namespace', () => {
      assert.strictEqual(simplifyLabel('module.submodule.Name', 20), 'Name');
    });

    test('should handle UUID prefix with namespace', () => {
      assert.strictEqual(simplifyLabel('12345678_module.Name', 20), 'module.Name');
    });

    test('should handle very short max length', () => {
      const result = simplifyLabel('test', 3);
      assert.strictEqual(result.length, 3);
      assert.ok(result.endsWith('…'));
    });
  });

  suite('buildTooltip', () => {
    test('should include node name', () => {
      const tooltip = buildTooltip({ name: 'MyNode', type: 'data' });
      assert.ok(tooltip.includes('MyNode'));
    });

    test('should include role if present', () => {
      const tooltip = buildTooltip({ name: 'MyNode', type: 'data', role: 'input' });
      assert.ok(tooltip.includes('Role: input'));
    });

    test('should include type for data nodes', () => {
      const tooltip = buildTooltip({ name: 'MyNode', type: 'data', cType: 'String' });
      assert.ok(tooltip.includes('Type: String'));
    });

    test('should show Operation for module nodes', () => {
      const tooltip = buildTooltip({ name: 'Transform', type: 'module' });
      assert.ok(tooltip.includes('Operation'));
    });

    test('should not show type for module nodes', () => {
      const tooltip = buildTooltip({ name: 'Transform', type: 'module', cType: 'String' });
      assert.ok(!tooltip.includes('Type:'));
    });
  });

  suite('getTypeColor', () => {
    test('should return unknown for undefined', () => {
      assert.strictEqual(getTypeColor(undefined), 'var(--type-unknown)');
    });

    test('should return string color for String type', () => {
      assert.strictEqual(getTypeColor('String'), 'var(--type-string)');
    });

    test('should return string color for text type (case insensitive)', () => {
      assert.strictEqual(getTypeColor('TEXT'), 'var(--type-string)');
    });

    test('should return int color for Int type', () => {
      assert.strictEqual(getTypeColor('Int'), 'var(--type-int)');
    });

    test('should return int color for integer type', () => {
      assert.strictEqual(getTypeColor('integer'), 'var(--type-int)');
    });

    test('should return int color for long type', () => {
      assert.strictEqual(getTypeColor('Long'), 'var(--type-int)');
    });

    test('should return float color for Float type', () => {
      assert.strictEqual(getTypeColor('Float'), 'var(--type-float)');
    });

    test('should return float color for double type', () => {
      assert.strictEqual(getTypeColor('double'), 'var(--type-float)');
    });

    test('should return float color for number type', () => {
      assert.strictEqual(getTypeColor('Number'), 'var(--type-float)');
    });

    test('should return boolean color for Boolean type', () => {
      assert.strictEqual(getTypeColor('Boolean'), 'var(--type-boolean)');
    });

    test('should return boolean color for bool type', () => {
      assert.strictEqual(getTypeColor('bool'), 'var(--type-boolean)');
    });

    test('should return list color for List type', () => {
      assert.strictEqual(getTypeColor('List[String]'), 'var(--type-list)');
    });

    test('should return list color for Array type', () => {
      assert.strictEqual(getTypeColor('Array'), 'var(--type-list)');
    });

    test('should return list color for Seq type', () => {
      assert.strictEqual(getTypeColor('Seq[Int]'), 'var(--type-list)');
    });

    test('should return record color for Record type', () => {
      assert.strictEqual(getTypeColor('Record'), 'var(--type-record)');
    });

    test('should return record color for Struct type', () => {
      assert.strictEqual(getTypeColor('Struct'), 'var(--type-record)');
    });

    test('should return optional color for Option type', () => {
      assert.strictEqual(getTypeColor('Option[String]'), 'var(--type-optional)');
    });

    test('should return optional color for Maybe type', () => {
      assert.strictEqual(getTypeColor('Maybe'), 'var(--type-optional)');
    });

    test('should return optional color for type with ?', () => {
      assert.strictEqual(getTypeColor('String?'), 'var(--type-optional)');
    });

    test('should return unknown for custom type', () => {
      assert.strictEqual(getTypeColor('CustomType'), 'var(--type-unknown)');
    });
  });

  suite('getTypeIndicator', () => {
    test('should return empty for undefined', () => {
      assert.strictEqual(getTypeIndicator(undefined), '');
    });

    test('should return T for String', () => {
      assert.strictEqual(getTypeIndicator('String'), 'T');
    });

    test('should return T for text', () => {
      assert.strictEqual(getTypeIndicator('text'), 'T');
    });

    test('should return # for Int', () => {
      assert.strictEqual(getTypeIndicator('Int'), '#');
    });

    test('should return # for integer', () => {
      assert.strictEqual(getTypeIndicator('integer'), '#');
    });

    test('should return .# for Float', () => {
      assert.strictEqual(getTypeIndicator('Float'), '.#');
    });

    test('should return .# for double', () => {
      assert.strictEqual(getTypeIndicator('double'), '.#');
    });

    test('should return ? for Boolean', () => {
      assert.strictEqual(getTypeIndicator('Boolean'), '?');
    });

    test('should return [] for List', () => {
      assert.strictEqual(getTypeIndicator('List[String]'), '[]');
    });

    test('should return [] for Array', () => {
      assert.strictEqual(getTypeIndicator('Array'), '[]');
    });

    test('should return {} for Record', () => {
      assert.strictEqual(getTypeIndicator('Record'), '{}');
    });

    test('should return {} for Struct', () => {
      assert.strictEqual(getTypeIndicator('Struct'), '{}');
    });

    test('should return ~ for Option', () => {
      assert.strictEqual(getTypeIndicator('Option'), '~');
    });

    test('should return ~ for type with ?', () => {
      assert.strictEqual(getTypeIndicator('Int?'), '~');
    });

    test('should return empty for unknown type', () => {
      assert.strictEqual(getTypeIndicator('CustomType'), '');
    });
  });

  suite('formatLargeNumber', () => {
    test('should format small numbers unchanged', () => {
      assert.strictEqual(formatLargeNumber(123), '123');
    });

    test('should format thousands with K suffix', () => {
      assert.strictEqual(formatLargeNumber(1000), '1.0K');
    });

    test('should format thousands with decimal', () => {
      assert.strictEqual(formatLargeNumber(1500), '1.5K');
    });

    test('should format millions with M suffix', () => {
      assert.strictEqual(formatLargeNumber(1000000), '1.0M');
    });

    test('should format millions with decimal', () => {
      assert.strictEqual(formatLargeNumber(2500000), '2.5M');
    });

    test('should format billions with B suffix', () => {
      assert.strictEqual(formatLargeNumber(1000000000), '1.0B');
    });

    test('should handle negative numbers', () => {
      assert.strictEqual(formatLargeNumber(-1500000), '-1.5M');
    });

    test('should handle zero', () => {
      assert.strictEqual(formatLargeNumber(0), '0');
    });

    test('should handle numbers just below threshold', () => {
      assert.strictEqual(formatLargeNumber(999), '999');
    });
  });

  suite('truncateValue', () => {
    test('should return empty for undefined', () => {
      assert.strictEqual(truncateValue(undefined, 10), '');
    });

    test('should return short values unchanged', () => {
      assert.strictEqual(truncateValue('hello', 10), 'hello');
    });

    test('should truncate with ellipsis', () => {
      const result = truncateValue('verylongtext', 8);
      assert.strictEqual(result, 'veryl...');
    });

    test('should handle exact length match', () => {
      assert.strictEqual(truncateValue('hello', 5), 'hello');
    });

    test('should handle very short max length', () => {
      const result = truncateValue('hello', 4);
      assert.strictEqual(result, 'h...');
    });
  });

  suite('formatJsonPreview', () => {
    test('should format empty array', () => {
      assert.strictEqual(formatJsonPreview([], 20), '[]');
    });

    test('should format single-element array', () => {
      const result = formatJsonPreview(['hello'], 20);
      assert.ok(result.includes('hello'));
    });

    test('should format multi-element array with count', () => {
      assert.strictEqual(formatJsonPreview([1, 2, 3], 20), '[3 items]');
    });

    test('should format empty object', () => {
      assert.strictEqual(formatJsonPreview({}, 20), '{}');
    });

    test('should format single-field object', () => {
      const result = formatJsonPreview({ key: 'value' }, 30);
      assert.ok(result.includes('key'));
    });

    test('should format multi-field object with count', () => {
      assert.strictEqual(formatJsonPreview({ a: 1, b: 2, c: 3 }, 20), '{3 fields}');
    });

    test('should format string with quotes', () => {
      assert.strictEqual(formatJsonPreview('hello', 20), '"hello"');
    });

    test('should format number', () => {
      assert.strictEqual(formatJsonPreview(123, 20), '123');
    });

    test('should format boolean', () => {
      assert.strictEqual(formatJsonPreview(true, 20), 'true');
    });

    test('should format null', () => {
      assert.strictEqual(formatJsonPreview(null, 20), 'null');
    });
  });

  suite('formatValuePreview', () => {
    test('should format null as "null"', () => {
      assert.strictEqual(formatValuePreview(null, 20), 'null');
    });

    test('should format undefined as "null"', () => {
      assert.strictEqual(formatValuePreview(undefined, 20), 'null');
    });

    test('should format empty string with quotes', () => {
      assert.strictEqual(formatValuePreview('', 20), '""');
    });

    test('should format boolean true', () => {
      assert.strictEqual(formatValuePreview('true', 20), 'true');
    });

    test('should format boolean false', () => {
      assert.strictEqual(formatValuePreview('false', 20), 'false');
    });

    test('should format small integer', () => {
      assert.strictEqual(formatValuePreview(123, 20), '123');
    });

    test('should format large integer with suffix', () => {
      assert.strictEqual(formatValuePreview(1500000, 20), '1.5M');
    });

    test('should format float with precision', () => {
      const result = formatValuePreview(3.14159265359, 20);
      assert.ok(result.length <= 20);
    });

    test('should format string with quotes', () => {
      const result = formatValuePreview('hello world', 20);
      assert.ok(result.includes('"'));
    });

    test('should parse and format JSON array string', () => {
      const result = formatValuePreview('[1, 2, 3]', 20);
      assert.ok(result.includes('items') || result.includes('['));
    });

    test('should parse and format JSON object string', () => {
      const result = formatValuePreview('{"key": "value"}', 30);
      assert.ok(result.includes('{'));
    });

    test('should truncate long values', () => {
      const result = formatValuePreview('this is a very long string value', 15);
      assert.ok(result.length <= 15);
    });
  });

  suite('createHexagonPoints', () => {
    test('should return valid SVG points string', () => {
      const result = createHexagonPoints(160, 44);
      assert.ok(typeof result === 'string');
      assert.ok(result.includes(','));
    });

    test('should create 6 points for hexagon', () => {
      const result = createHexagonPoints(160, 44);
      const points = result.split(' ');
      assert.strictEqual(points.length, 6);
    });

    test('should include corner and edge points', () => {
      const result = createHexagonPoints(160, 44);
      // Should include the right point at full width
      assert.ok(result.includes('160,'));
      // Should include left point at 0
      assert.ok(result.includes('0,'));
    });

    test('should have symmetric left/right points', () => {
      const result = createHexagonPoints(160, 44);
      const points = result.split(' ').map(p => p.split(',').map(Number));
      // Left point y should equal right point y (both at height/2)
      const leftPoint = points.find(p => p[0] === 0);
      const rightPoint = points.find(p => p[0] === 160);
      assert.ok(leftPoint && rightPoint);
      assert.strictEqual(leftPoint[1], rightPoint[1]);
    });
  });

  suite('computeLayout', () => {
    const emptyDag: DagStructure = {
      modules: {},
      data: {},
      inEdges: [],
      outEdges: [],
      declaredOutputs: []
    };

    const singleNodeDag: DagStructure = {
      modules: {},
      data: { 'node1': { name: 'input', cType: 'String' } },
      inEdges: [],
      outEdges: [],
      declaredOutputs: []
    };

    const simpleDag: DagStructure = {
      modules: {
        'mod1': { name: 'Transform', consumes: { text: 'data1' }, produces: { result: 'data2' } }
      },
      data: {
        'data1': { name: 'input', cType: 'String' },
        'data2': { name: 'output', cType: 'String' }
      },
      inEdges: [['data1', 'mod1']],
      outEdges: [['mod1', 'data2']],
      declaredOutputs: ['data2']
    };

    const multiLayerDag: DagStructure = {
      modules: {
        'mod1': { name: 'Step1', consumes: {}, produces: {} },
        'mod2': { name: 'Step2', consumes: {}, produces: {} },
        'mod3': { name: 'Step3', consumes: {}, produces: {} }
      },
      data: {
        'data1': { name: 'input', cType: 'String' },
        'data2': { name: 'mid1', cType: 'String' },
        'data3': { name: 'mid2', cType: 'String' },
        'data4': { name: 'output', cType: 'String' }
      },
      inEdges: [['data1', 'mod1'], ['data2', 'mod2'], ['data3', 'mod3']],
      outEdges: [['mod1', 'data2'], ['mod2', 'data3'], ['mod3', 'data4']],
      declaredOutputs: ['data4']
    };

    test('should handle empty DAG', () => {
      const result = computeLayout(emptyDag);
      assert.deepStrictEqual(result.nodes, {});
      assert.deepStrictEqual(result.edges, []);
    });

    test('should return valid bounds for empty DAG', () => {
      const result = computeLayout(emptyDag);
      assert.ok(result.bounds.maxX > result.bounds.minX);
      assert.ok(result.bounds.maxY > result.bounds.minY);
    });

    test('should handle single node DAG', () => {
      const result = computeLayout(singleNodeDag);
      assert.strictEqual(Object.keys(result.nodes).length, 1);
      const node = result.nodes['node1'];
      assert.ok(node);
      assert.strictEqual(node.type, 'data');
      assert.strictEqual(node.role, 'input'); // No incoming edges
    });

    test('should assign positions to all nodes', () => {
      const result = computeLayout(simpleDag);
      Object.values(result.nodes).forEach(node => {
        assert.ok(typeof node.x === 'number');
        assert.ok(typeof node.y === 'number');
      });
    });

    test('should classify input nodes correctly', () => {
      const result = computeLayout(simpleDag);
      const inputNode = result.nodes['data1'];
      assert.strictEqual(inputNode.role, 'input');
    });

    test('should classify output nodes correctly', () => {
      const result = computeLayout(simpleDag);
      const outputNode = result.nodes['data2'];
      assert.strictEqual(outputNode.role, 'output');
    });

    test('should classify module nodes as operations', () => {
      const result = computeLayout(simpleDag);
      const moduleNode = result.nodes['mod1'];
      assert.strictEqual(moduleNode.role, 'operation');
    });

    test('should use different heights for different node roles', () => {
      const result = computeLayout(simpleDag);
      const inputNode = result.nodes['data1'];
      const outputNode = result.nodes['data2'];
      const moduleNode = result.nodes['mod1'];

      assert.strictEqual(inputNode.height, 36);  // inputHeight
      assert.strictEqual(outputNode.height, 44); // outputHeight
      assert.strictEqual(moduleNode.height, 60); // moduleHeight
    });

    test('should combine all edges in result', () => {
      const result = computeLayout(simpleDag);
      assert.strictEqual(result.edges.length, 2); // 1 inEdge + 1 outEdge
    });

    test('should calculate valid bounds', () => {
      const result = computeLayout(simpleDag);
      const nodes = Object.values(result.nodes);
      nodes.forEach(node => {
        assert.ok((node.x ?? 0) >= result.bounds.minX);
        assert.ok((node.x ?? 0) <= result.bounds.maxX);
        assert.ok((node.y ?? 0) >= result.bounds.minY);
        assert.ok((node.y ?? 0) <= result.bounds.maxY);
      });
    });

    test('should layer nodes correctly for TB layout', () => {
      const result = computeLayout(multiLayerDag, 'TB');
      // In TB layout, later layers should have higher y values
      const input = result.nodes['data1'];
      const output = result.nodes['data4'];
      assert.ok((input.y ?? 0) < (output.y ?? 0));
    });

    test('should layer nodes correctly for LR layout', () => {
      const result = computeLayout(multiLayerDag, 'LR');
      // In LR layout, later layers should have higher x values
      const input = result.nodes['data1'];
      const output = result.nodes['data4'];
      assert.ok((input.x ?? 0) < (output.x ?? 0));
    });

    test('should handle DAG with cycle gracefully', () => {
      const cyclicDag: DagStructure = {
        modules: {},
        data: {
          'a': { name: 'a', cType: 'String' },
          'b': { name: 'b', cType: 'String' }
        },
        inEdges: [['a', 'b'], ['b', 'a']], // Circular reference
        outEdges: [],
        declaredOutputs: []
      };

      // Should not throw
      const result = computeLayout(cyclicDag);
      assert.strictEqual(Object.keys(result.nodes).length, 2);
    });
  });

  suite('calculateEdgePath', () => {
    const fromNode: DagNode = {
      id: 'from',
      type: 'data',
      role: 'input',
      name: 'from',
      width: 160,
      height: 36,
      x: 100,
      y: 50
    };

    const toNode: DagNode = {
      id: 'to',
      type: 'data',
      role: 'output',
      name: 'to',
      width: 160,
      height: 44,
      x: 100,
      y: 200
    };

    test('should return valid SVG path for TB layout', () => {
      const path = calculateEdgePath(fromNode, toNode, 'TB');
      assert.ok(path.startsWith('M'));
      assert.ok(path.includes('C'));
    });

    test('should start from bottom of source node in TB layout', () => {
      const path = calculateEdgePath(fromNode, toNode, 'TB');
      // Should start at fromNode.y + height/2 = 50 + 18 = 68
      assert.ok(path.includes('68'));
    });

    test('should end at top of target node in TB layout', () => {
      const path = calculateEdgePath(fromNode, toNode, 'TB');
      // Should end at toNode.y - height/2 = 200 - 22 = 178
      assert.ok(path.includes('178'));
    });

    test('should return valid SVG path for LR layout', () => {
      const path = calculateEdgePath(fromNode, toNode, 'LR');
      assert.ok(path.startsWith('M'));
      assert.ok(path.includes('C'));
    });

    test('should use horizontal connections in LR layout', () => {
      const leftNode: DagNode = { ...fromNode, x: 50, y: 100 };
      const rightNode: DagNode = { ...toNode, x: 300, y: 100 };
      const path = calculateEdgePath(leftNode, rightNode, 'LR');
      // Should use fromNode.x + width/2 and toNode.x - width/2
      assert.ok(path.includes('130')); // 50 + 80
      assert.ok(path.includes('220')); // 300 - 80
    });

    test('should handle nodes with undefined positions', () => {
      const nodeWithoutPos: DagNode = {
        id: 'no-pos',
        type: 'data',
        role: 'input',
        name: 'no-pos',
        width: 160,
        height: 36
      };
      // Should not throw
      const path = calculateEdgePath(nodeWithoutPos, toNode, 'TB');
      assert.ok(typeof path === 'string');
    });
  });

  suite('Integration: Layout with Different Structures', () => {
    test('should handle wide DAG (many nodes in same layer)', () => {
      const wideDag: DagStructure = {
        modules: {},
        data: {
          'a': { name: 'a', cType: 'String' },
          'b': { name: 'b', cType: 'String' },
          'c': { name: 'c', cType: 'String' },
          'd': { name: 'd', cType: 'String' },
          'e': { name: 'e', cType: 'String' }
        },
        inEdges: [],
        outEdges: [],
        declaredOutputs: []
      };

      const result = computeLayout(wideDag, 'TB');
      // All nodes are inputs with no edges, so they should be in the same layer
      // and spread horizontally
      const nodes = Object.values(result.nodes);
      const yValues = new Set(nodes.map(n => n.y));
      assert.strictEqual(yValues.size, 1); // All same y in TB layout
    });

    test('should handle deep DAG (many layers)', () => {
      const deepDag: DagStructure = {
        modules: {},
        data: {
          'a': { name: 'a', cType: 'String' },
          'b': { name: 'b', cType: 'String' },
          'c': { name: 'c', cType: 'String' },
          'd': { name: 'd', cType: 'String' }
        },
        inEdges: [['a', 'b'], ['b', 'c'], ['c', 'd']],
        outEdges: [],
        declaredOutputs: ['d']
      };

      const result = computeLayout(deepDag, 'TB');
      const nodes = Object.values(result.nodes);

      // Each node should be in a different layer (different y)
      const yValues = new Set(nodes.map(n => n.y));
      assert.strictEqual(yValues.size, 4);
    });

    test('should produce consistent layouts', () => {
      const dag: DagStructure = {
        modules: { 'm': { name: 'M', consumes: {}, produces: {} } },
        data: {
          'a': { name: 'a', cType: 'String' },
          'b': { name: 'b', cType: 'String' }
        },
        inEdges: [['a', 'm']],
        outEdges: [['m', 'b']],
        declaredOutputs: ['b']
      };

      const result1 = computeLayout(dag, 'TB');
      const result2 = computeLayout(dag, 'TB');

      // Same input should produce same output
      assert.deepStrictEqual(
        Object.keys(result1.nodes).sort(),
        Object.keys(result2.nodes).sort()
      );
    });
  });
});
