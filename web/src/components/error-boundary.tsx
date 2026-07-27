"use client";

import { Component, type ErrorInfo, type ReactNode } from "react";
import { logClientError } from "@/lib/log-client-error";
import { Button } from "@/components/ui/button";

type Props = { children: ReactNode; fallbackTitle?: string };
type State = { hasError: boolean };

/**
 * Catches render errors so users see a calm recovery UI instead of a red stack dump.
 */
export class AppErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    logClientError("AppErrorBoundary", error, {
      componentStack: info.componentStack,
    });
  }

  private reset = () => {
    this.setState({ hasError: false });
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="mx-auto flex max-w-lg flex-col items-center justify-center px-6 py-20 text-center">
          <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-100">
            {this.props.fallbackTitle ?? "Something went wrong"}
          </h2>
          <p className="mt-2 text-sm text-zinc-500">
            Please try again. If the problem continues, refresh the page or
            contact support.
          </p>
          <div className="mt-6 flex gap-3">
            <Button type="button" variant="secondary" onClick={this.reset}>
              Try again
            </Button>
            <Button
              type="button"
              onClick={() => {
                if (typeof window !== "undefined") window.location.reload();
              }}
            >
              Refresh page
            </Button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
