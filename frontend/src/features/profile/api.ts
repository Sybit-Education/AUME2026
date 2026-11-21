export function loadProfile() {
  return new Promise<{ greeting: string }>((resolve) => {
    setTimeout(() => {
      resolve({ greeting: 'Hello from the profile!' })
    }, 1000)
  })}