
import React, { useState } from 'react';
import { MessageSquare, Send, X, Bot, Sparkles } from 'lucide-react';
import { askGeminiAssistant } from '../services/ai';

export const AIAssistant: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<{role: 'user' | 'bot', text: string}[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  const handleSend = async () => {
    if (!input.trim()) return;
    
    const userMsg = input;
    setInput('');
    setMessages(prev => [...prev, { role: 'user', text: userMsg }]);
    setIsLoading(true);

    const response = await askGeminiAssistant(userMsg);
    setMessages(prev => [...prev, { role: 'bot', text: response }]);
    setIsLoading(false);
  };

  return (
    <div className="fixed bottom-24 left-6 z-50">
      {isOpen ? (
        <div className="bg-white w-[85vw] max-w-[350px] h-[450px] rounded-3xl shadow-2xl border border-slate-200 flex flex-col overflow-hidden animate-in zoom-in-95 duration-200">
          <div className="bg-gradient-to-r from-primary to-emerald-700 p-4 text-white flex justify-between items-center">
            <div className="flex items-center gap-2">
              <Bot size={20} />
              <span className="font-bold text-sm tracking-tight">GeoAsistente IA</span>
            </div>
            <button onClick={() => setIsOpen(false)} className="p-1 hover:bg-white/20 rounded-full">
              <X size={18} />
            </button>
          </div>

          <div className="flex-1 overflow-y-auto p-4 space-y-4 bg-slate-50">
            {messages.length === 0 && (
              <div className="text-center py-10">
                <div className="bg-emerald-100 w-12 h-12 rounded-full flex items-center justify-center mx-auto mb-3">
                  <Sparkles className="text-emerald-600" />
                </div>
                <p className="text-xs text-slate-500 font-medium px-6">¿Tienes dudas sobre una parcela o la normativa SIGPAC? Pregúntame.</p>
              </div>
            )}
            {messages.map((m, i) => (
              <div key={i} className={`flex ${m.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                <div className={`max-w-[80%] p-3 rounded-2xl text-sm ${
                  m.role === 'user' 
                    ? 'bg-primary text-white rounded-tr-none' 
                    : 'bg-white border border-slate-200 text-slate-700 rounded-tl-none shadow-sm'
                }`}>
                  {m.text}
                </div>
              </div>
            ))}
            {isLoading && (
              <div className="flex justify-start">
                <div className="bg-white border border-slate-200 p-3 rounded-2xl rounded-tl-none shadow-sm flex gap-1">
                  <div className="w-1.5 h-1.5 bg-slate-300 rounded-full animate-bounce"></div>
                  <div className="w-1.5 h-1.5 bg-slate-300 rounded-full animate-bounce [animation-delay:0.2s]"></div>
                  <div className="w-1.5 h-1.5 bg-slate-300 rounded-full animate-bounce [animation-delay:0.4s]"></div>
                </div>
              </div>
            )}
          </div>

          <div className="p-3 border-t bg-white">
            <div className="flex gap-2">
              <input 
                type="text" 
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && handleSend()}
                placeholder="Escribe tu duda..."
                className="flex-1 bg-slate-100 border-none rounded-xl px-4 py-2 text-sm focus:ring-2 focus:ring-primary/20 outline-none"
              />
              <button 
                onClick={handleSend}
                className="p-2 bg-primary text-white rounded-xl hover:scale-105 active:scale-95 transition-transform"
              >
                <Send size={18} />
              </button>
            </div>
          </div>
        </div>
      ) : (
        <button 
          onClick={() => setIsOpen(true)}
          className="w-12 h-12 bg-emerald-600 text-white rounded-full shadow-lg flex items-center justify-center hover:scale-110 active:scale-90 transition-all animate-bounce"
        >
          <MessageSquare size={20} />
        </button>
      )}
    </div>
  );
};
