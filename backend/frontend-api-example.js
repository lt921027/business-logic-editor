// 前端与后端交互示例代码

// API基础配置
const API_BASE_URL = 'http://localhost:8080/api';

// 1. 保存业务逻辑
async function saveBusinessLogic(logicData) {
  try {
    const response = await fetch(`${API_BASE_URL}/business-logic`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(logicData),
    });

    const result = await response.json();

    if (result.code === 200) {
      console.log('保存成功:', result.data);
      return result.data;
    } else {
      console.error('保存失败:', result.message);
      throw new Error(result.message);
    }
  } catch (error) {
    console.error('请求失败:', error);
    throw error;
  }
}

// 2. 更新业务逻辑
async function updateBusinessLogic(id, logicData) {
  try {
    const response = await fetch(`${API_BASE_URL}/business-logic/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(logicData),
    });

    const result = await response.json();

    if (result.code === 200) {
      console.log('更新成功:', result.data);
      return result.data;
    } else {
      console.error('更新失败:', result.message);
      throw new Error(result.message);
    }
  } catch (error) {
    console.error('请求失败:', error);
    throw error;
  }
}

// 3. 查询业务逻辑
async function getBusinessLogic(id) {
  try {
    const response = await fetch(`${API_BASE_URL}/business-logic/${id}`, {
      method: 'GET',
    });

    const result = await response.json();

    if (result.code === 200) {
      console.log('查询成功:', result.data);
      return result.data;
    } else {
      console.error('查询失败:', result.message);
      throw new Error(result.message);
    }
  } catch (error) {
    console.error('请求失败:', error);
    throw error;
  }
}

// 4. 查询所有业务逻辑
async function listAllBusinessLogics() {
  try {
    const response = await fetch(`${API_BASE_URL}/business-logic`, {
      method: 'GET',
    });

    const result = await response.json();

    if (result.code === 200) {
      console.log('查询成功:', result.data);
      return result.data;
    } else {
      console.error('查询失败:', result.message);
      throw new Error(result.message);
    }
  } catch (error) {
    console.error('请求失败:', error);
    throw error;
  }
}

// 5. 删除业务逻辑
async function deleteBusinessLogic(id) {
  try {
    const response = await fetch(`${API_BASE_URL}/business-logic/${id}`, {
      method: 'DELETE',
    });

    const result = await response.json();

    if (result.code === 200) {
      console.log('删除成功');
      return true;
    } else {
      console.error('删除失败:', result.message);
      throw new Error(result.message);
    }
  } catch (error) {
    console.error('请求失败:', error);
    throw error;
  }
}

// 6. 执行业务逻辑
async function executeBusinessLogic(id, inputData) {
  try {
    const response = await fetch(`${API_BASE_URL}/business-logic/${id}/execute`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(inputData),
    });

    const result = await response.json();

    if (result.code === 200) {
      console.log('执行成功:', result.data);
      return result.data;
    } else {
      console.error('执行失败:', result.message);
      throw new Error(result.message);
    }
  } catch (error) {
    console.error('请求失败:', error);
    throw error;
  }
}

// 使用示例

// 示例1：保存业务逻辑
const exampleLogicData = {
  name: '订单处理逻辑',
  description: '处理订单的业务逻辑',
  jsonInput: JSON.stringify({
    name: '张三',
    age: 25,
    email: 'zhangsan@example.com',
    order: {
      id: 1001,
      amount: 999.99
    }
  }),
  logicSteps: [
    {
      stepOrder: 1,
      functionCategory: 'direct',
      mappedField: 'name',
      outputVar: 'userName',
      comment: '映射用户名'
    },
    {
      stepOrder: 2,
      functionCategory: 'calculation',
      calculationSteps: [
        {
          logicOperator: '',
          functionCategory: 'number',
          filterFunction: 'greaterThan',
          operands: [
            {
              type: 'field',
              field: 'age'
            },
            {
              type: 'value',
              value: '18'
            }
          ]
        }
      ],
      outputVar: 'isAdult',
      comment: '判断是否成年'
    },
    {
      stepOrder: 3,
      functionCategory: 'filter',
      filterScope: 'order',
      filterItems: [
        {
          type: 'condition',
          logicOperator: '',
          functionCategory: 'number',
          filterFunction: 'greaterThan',
          operands: [
            {
              type: 'field',
              field: 'order.amount'
            },
            {
              type: 'value',
              value: '500'
            }
          ]
        }
      ],
      filterLogic: [
        {
          executionType: 'returnValue',
          returnType: 'fixed',
          fixedValue: '高价值订单',
          comment: '返回高价值订单标识'
        }
      ],
      reverseLogic: [
        {
          executionType: 'returnValue',
          returnType: 'fixed',
          fixedValue: '普通订单',
          comment: '返回普通订单标识'
        }
      ],
      outputVar: 'orderType',
      comment: '判断订单类型'
    }
  ]
};

// 保存逻辑
saveBusinessLogic(exampleLogicData)
  .then(result => {
    console.log('业务逻辑已保存，ID:', result.id);
    // 可以在这里跳转到详情页或执行逻辑
  })
  .catch(error => {
    console.error('保存失败:', error);
    alert('保存失败: ' + error.message);
  });

// 示例2：加载并反显业务逻辑
const logicId = 1;
getBusinessLogic(logicId)
  .then(result => {
    console.log('业务逻辑详情:', result);
    
    // 反显到前端编辑器
    // 设置JSON输入
    document.querySelector('.json-textarea').value = result.jsonInput;
    
    // 解析JSON生成字段树
    parseJsonAndBuildTree(result.jsonInput);
    
    // 清空现有步骤
    logicSteps.value = [];
    
    // 反显每个步骤
    result.logicSteps.forEach(step => {
      const newStep = {
        id: step.id,
        functionCategory: step.functionCategory,
        field: step.field,
        functionName: step.functionName,
        params: step.params,
        customExpression: step.customExpression,
        outputVar: step.outputVar,
        comment: step.comment,
        filterScope: step.filterScope,
        mappedField: step.mappedField,
        calculationSteps: step.calculationSteps,
        filterItems: step.filterItems,
        filterLogic: step.filterLogic,
        reverseLogic: step.reverseLogic,
        collapsed: step.collapsed
      };
      
      logicSteps.value.push(newStep);
    });
    
    // 更新字段树选中状态
    updateFieldTreeSelection();
  })
  .catch(error => {
    console.error('加载失败:', error);
    alert('加载失败: ' + error.message);
  });

// 示例3：执行业务逻辑
const inputData = {
  name: '李四',
  age: 30,
  email: 'lisi@example.com',
  order: {
    id: 1002,
    amount: 1500.00
  }
};

executeBusinessLogic(logicId, inputData)
  .then(result => {
    console.log('执行结果:', result);
    alert('执行成功: ' + JSON.stringify(result, null, 2));
  })
  .catch(error => {
    console.error('执行失败:', error);
    alert('执行失败: ' + error.message);
  });

// 示例4：更新业务逻辑
const updatedLogicData = {
  ...exampleLogicData,
  name: '订单处理逻辑（已更新）',
  description: '更新后的订单处理逻辑'
};

updateBusinessLogic(logicId, updatedLogicData)
  .then(result => {
    console.log('更新成功:', result);
    alert('更新成功');
  })
  .catch(error => {
    console.error('更新失败:', error);
    alert('更新失败: ' + error.message);
  });

// 示例5：删除业务逻辑
deleteBusinessLogic(logicId)
  .then(() => {
    console.log('删除成功');
    alert('删除成功');
    // 刷新列表
    listAllBusinessLogics();
  })
  .catch(error => {
    console.error('删除失败:', error);
    alert('删除失败: ' + error.message);
  });

// 示例6：查询所有业务逻辑
listAllBusinessLogics()
  .then(result => {
    console.log('所有业务逻辑:', result);
    // 渲染逻辑列表
    renderLogicList(result);
  })
  .catch(error => {
    console.error('查询失败:', error);
    alert('查询失败: ' + error.message);
  });

// Vue 3 集成示例
import { ref, onMounted } from 'vue';

export default {
  setup() {
    const logicSteps = ref([]);
    const currentLogicId = ref(null);
    const jsonInput = ref('');
    
    // 保存业务逻辑
    const saveLogic = async () => {
      const logicData = {
        name: '我的业务逻辑',
        description: '业务逻辑描述',
        jsonInput: jsonInput.value,
        logicSteps: logicSteps.value
      };
      
      try {
        const result = await saveBusinessLogic(logicData);
        currentLogicId.value = result.id;
        alert('保存成功！');
      } catch (error) {
        alert('保存失败: ' + error.message);
      }
    };
    
    // 加载业务逻辑
    const loadLogic = async (id) => {
      try {
        const result = await getBusinessLogic(id);
        currentLogicId.value = result.id;
        jsonInput.value = result.jsonInput;
        logicSteps.value = result.logicSteps;
        
        // 解析JSON并生成字段树
        parseJsonAndBuildTree(jsonInput.value);
      } catch (error) {
        alert('加载失败: ' + error.message);
      }
    };
    
    // 执行业务逻辑
    const executeLogic = async () => {
      if (!currentLogicId.value) {
        alert('请先保存业务逻辑');
        return;
      }
      
      const inputData = JSON.parse(jsonInput.value);
      
      try {
        const result = await executeBusinessLogic(currentLogicId.value, inputData);
        alert('执行结果: ' + JSON.stringify(result, null, 2));
      } catch (error) {
        alert('执行失败: ' + error.message);
      }
    };
    
    return {
      logicSteps,
      jsonInput,
      saveLogic,
      loadLogic,
      executeLogic
    };
  }
};
