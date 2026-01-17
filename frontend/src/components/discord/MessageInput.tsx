import React, { useState, useRef } from 'react';

interface MessageInputProps {
  onSendMessage: (content: string) => void;
  placeholder?: string;
}

export const MessageInput: React.FC<MessageInputProps> = ({ onSendMessage, placeholder }) => {
  const [content, setContent] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const handleSubmit = (e?: React.FormEvent) => {
    e?.preventDefault();
    if (!content.trim()) return;
    onSendMessage(content);
    setContent('');
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setContent(e.target.value);
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = `${textareaRef.current.scrollHeight}px`;
    }
  };

  return (
    <div className="bg-theme-lighter/30 backdrop-blur-md rounded-xl p-3 flex items-center border border-white/5 focus-within:border-theme-primary/50 focus-within:ring-1 focus-within:ring-theme-primary/50 transition-all shadow-2xl">
      <div className="w-8 h-8 rounded-full bg-theme-primary/20 text-theme-primary mr-3 shrink-0 cursor-pointer hover:bg-theme-primary hover:text-white transition-colors flex items-center justify-center font-bold text-lg">
          +
      </div>
      <textarea
        ref={textareaRef}
        value={content}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        placeholder={placeholder || "Message"}
        rows={1}
        className="bg-transparent text-theme-text w-full resize-none outline-none max-h-[50vh] overflow-y-auto font-light placeholder-theme-muted/50"
        style={{ minHeight: '24px' }}
      />
    </div>
  );
};
