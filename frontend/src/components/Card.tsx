import React, { HTMLAttributes } from 'react';
import { cn } from '../utils';

export const Card: React.FC<HTMLAttributes<HTMLDivElement>> = ({ className, children, ...props }) => {
  return (
    <div
      className={cn('bg-white shadow-md rounded-lg p-6', className)}
      {...props}
    >
      {children}
    </div>
  );
};
