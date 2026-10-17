'use strict';
// Presets are editable connection hints, not a provider allowlist or a fixed model catalog.
const modelProviders=[
  {id:'custom',name:'自定义 / 其他服务商',url:'',protocol:'OPENAI',note:'填写服务商的 API 前缀和模型 ID。支持 OpenAI Chat Completions 或 Anthropic Messages；不支持的私有协议需经兼容网关接入。'},
  {id:'deepseek',name:'DeepSeek',url:'https://api.deepseek.com/v1',protocol:'OPENAI',model:'deepseek-chat',docs:'https://api-docs.deepseek.com/',note:'可查询账户余额。'},
  {id:'openai',name:'OpenAI',url:'https://api.openai.com/v1',protocol:'OPENAI',docs:'https://platform.openai.com/docs/api-reference/chat',note:'选择支持 Chat Completions 的文本模型；仅支持 Responses 的模型不适用。'},
  {id:'anthropic',name:'Anthropic / Claude',url:'https://api.anthropic.com/v1',protocol:'ANTHROPIC',docs:'https://platform.claude.com/docs/en/api/messages',note:'原生 Messages 协议，支持普通请求和流式输出；默认最大输出 4096 Token，可在智能体中调整。'},
  {id:'gemini',name:'Google / Gemini',url:'https://generativelanguage.googleapis.com/v1beta/openai',protocol:'OPENAI',docs:'https://ai.google.dev/gemini-api/docs/openai',note:'使用 Gemini API Key 和官方 OpenAI 兼容地址。'},
  {id:'qwen',name:'阿里云百炼 / 通义千问（北京）',url:'https://dashscope.aliyuncs.com/compatible-mode/v1',protocol:'OPENAI',model:'qwen-plus',docs:'https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope',note:'预设为北京地域；其他地域请按官方文档修改地址，并使用对应地域的 Key。'},
  {id:'zhipu',name:'智谱 / GLM',url:'https://open.bigmodel.cn/api/paas/v4',protocol:'OPENAI',docs:'https://docs.bigmodel.cn/cn/guide/develop/openai/introduction',note:'普通按量 API。Coding Plan 有独立地址和使用限制，请按购买的服务调整。'},
  {id:'moonshot',name:'月之暗面 / Kimi',url:'https://api.moonshot.cn/v1',protocol:'OPENAI',docs:'https://platform.moonshot.cn/docs/intro',note:'填写平台实际开放的模型 ID；国际站 Key 请使用对应的国际站地址。'},
  {id:'doubao',name:'火山方舟 / 豆包（北京）',url:'https://ark.cn-beijing.volces.com/api/v3',protocol:'OPENAI',docs:'https://docs.volcengine.com/docs/ark/context-management?lang=zh',note:'模型 ID 可为已开通的模型或推理接入点 ID；不是网页中的显示名称。'},
  {id:'hunyuan',name:'腾讯 / 混元',url:'https://api.hunyuan.cloud.tencent.com/v1',protocol:'OPENAI',docs:'https://cloud.tencent.cn/document/product/1729/111007',note:'使用混元 OpenAI 兼容接口专用 API Key，不是腾讯云 SecretId/SecretKey。'},
  {id:'siliconflow',name:'硅基流动 / SiliconFlow',url:'https://api.siliconflow.cn/v1',protocol:'OPENAI',docs:'https://docs.siliconflow.cn/en/userguide/quickstart',note:'聚合多种开源模型；模型 ID 需包含服务商要求的完整路径。'},
  {id:'openrouter',name:'OpenRouter / 聚合网关',url:'https://openrouter.ai/api/v1',protocol:'OPENAI',docs:'https://openrouter.ai/docs/quickstart',note:'可接入多家厂商模型；余额面板显示的是 Key 额度，不是钱包余额。'},
  {id:'groq',name:'Groq',url:'https://api.groq.com/openai/v1',protocol:'OPENAI',docs:'https://console.groq.com/docs/openai',note:'选择账号可用的文本模型；不是 xAI 的 Grok。'},
  {id:'mistral',name:'Mistral',url:'https://api.mistral.ai/v1',protocol:'OPENAI',docs:'https://docs.mistral.ai/resources/migration-guides',note:'使用 Chat Completions 兼容接口。'},
  {id:'ollama',name:'Ollama / 本机模型',url:'http://127.0.0.1:11434/v1',protocol:'OPENAI',docs:'https://docs.ollama.com/api/openai-compatibility',note:'先启动 Ollama 并下载模型。无鉴权的本机 Ollama 可填占位 Key：ollama；不要填写真实云端 Key。'}
];
function matchingModelProvider(url,protocol){return modelProviders.find(p=>p.url&&p.url===url.replace(/\/+$/,'')&&p.protocol===protocol)||modelProviders[0];}
