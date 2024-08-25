import os

def clear_fd_results(directory):
    # Walk through the directory
    for root, dirs, files in os.walk(directory):
        for file in files:
            if file == "FD_results.txt":
                # Construct the full file path
                file_path = os.path.join(root, file)
                try:
                    # Open the file in write mode to clear its content
                    with open(file_path, 'w') as f:
                        f.truncate(0)
                    print(f"Cleared: {file_path}")
                except Exception as e:
                    print(f"Error clearing {file_path}: {e}")

if __name__ == "__main__":
    # Replace 'your_directory' with the path to your directory
    directory = 'Sakila_denormalized'
    clear_fd_results(directory)

