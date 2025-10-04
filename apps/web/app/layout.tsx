export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-gray-50 text-gray-900">
        <div className="max-w-5xl mx-auto p-6">
          <header className="mb-6">
            <h1 className="text-3xl font-bold">AI Ant Farm</h1>
            <p className="text-sm text-gray-600">Swarms of model-driven "ants" collaborating in a shared farm.</p>
          </header>
          {children}
        </div>
      </body>
    </html>
  );
}
